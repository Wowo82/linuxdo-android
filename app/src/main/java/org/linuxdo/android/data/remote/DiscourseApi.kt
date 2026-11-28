package org.linuxdo.android.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class HttpException(val code: Int, val body: String, val url: String) :
    IOException("HTTP $code for $url")

class ApiException(val kind: Kind, message: String, cause: Throwable? = null) :
    IOException(message, cause) {
    enum class Kind { NETWORK, AUTH, CSRF, PARSE, RATE_LIMIT, SERVER, UNKNOWN }

    val isCsrfFailure: Boolean get() = kind == Kind.CSRF
}

/**
 * Discourse REST 封装：拼 URL、发请求、反序列化。业务语义留给 Repository。
 *
 * CSRF：Discourse 对 browser session 的**非 GET** 请求强制校验
 * `X-CSRF-Token`，只靠 cookie + `X-Requested-With` 不豁免。
 * 这里在写操作收到 403 时自动取一次新 token 并重放（只重放一次，避免死循环）。
 */
class DiscourseApi(
    private val baseUrlProvider: () -> String,
    private val clientProvider: () -> OkHttpClient,
    private val csrfProvider: () -> String? = { null },
    private val csrfPersist: (String) -> Unit = {},
) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = false
    }

    private val baseUrl: String get() = baseUrlProvider().trimEnd('/')
    private val client: OkHttpClient get() = clientProvider()

    // ---------- URL ----------

    fun urlFor(path: String, params: Map<String, String?> = emptyMap()): HttpUrl {
        val full = when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("/") -> baseUrl + path
            else -> "$baseUrl/$path"
        }
        val parsed = full.toHttpUrlOrNull()
            ?: throw ApiException(ApiException.Kind.UNKNOWN, "无法解析 URL: $full")
        if (params.isEmpty()) return parsed
        val b = parsed.newBuilder()
        params.forEach { (k, v) -> if (v != null) b.addQueryParameter(k, v) }
        return b.build()
    }

    // ---------- CSRF ----------

    /**
     * 取一次 CSRF token 并落盘。GET 请求，不会再触发写操作的 403 分支。
     * 站点没开 CSRF（或接口 404）时返回 null，调用方按「没有 token」继续。
     */
    suspend fun refreshCsrf(): String? {
        val raw = runCatching { getRaw("/session/csrf.json") }.getOrNull() ?: return null
        val token = runCatching {
            (json.parseToJsonElement(raw) as? JsonObject)?.get("csrf")?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        csrfPersist(token)
        return token
    }

    // ---------- 读 ----------

    suspend fun getRaw(path: String, params: Map<String, String?> = emptyMap()): String {
        val req = Request.Builder().url(urlFor(path, params)).get().build()
        return execute(req)
    }

    suspend fun healthCheck(): Boolean = try {
        getRaw("/site/basic-info.json").contains("title")
    } catch (e: Exception) {
        false
    }

    // ---------- 写 ----------

    suspend fun postForm(path: String, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(urlFor(path)).post(body).build()
        return execute(req)
    }

    suspend fun putForm(path: String, form: Map<String, String>): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(urlFor(path)).put(body).build()
        return execute(req)
    }

    suspend fun deleteForm(path: String, form: Map<String, String> = emptyMap()): String {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        val req = Request.Builder().url(urlFor(path)).delete(body).build()
        return execute(req)
    }

    suspend fun postJson(path: String, payload: String): String {
        val body = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(urlFor(path)).post(body).build()
        return execute(req)
    }

    /** 上传附件：先把字节传上去拿 short_url，再把 URL 塞进正文 */
    suspend fun uploadFile(path: String, fileName: String, mime: String, bytes: ByteArray): String {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
            .addFormDataPart("type", "composer")
            .build()
        val req = Request.Builder().url(urlFor(path)).post(multipart).build()
        return execute(req)
    }

    // ---------- 底层执行 ----------

    /**
     * @param allowCsrfRetry 只允许重试一次，防止 403 → 刷新 → 403 死循环
     */
    private suspend fun execute(req: Request, allowCsrfRetry: Boolean = true): String {
        val isWrite = req.method != "GET" && req.method != "HEAD"
        val sent = if (isWrite) attachCsrf(req) else req
        val (body, code, message) = await(sent)
        if (code in 200..299) return body

        // 只有「看起来是 CSRF 失败」的 403 才去换 token；
        // 普通无权限 / 被封禁的 403 重试没有意义，反而多打一次 csrf 接口
        if (code == 403 && isWrite && allowCsrfRetry && looksLikeCsrfFailure(body)) {
            val before = csrfProvider()
            val fresh = refreshCsrf()
            if (!fresh.isNullOrBlank() && fresh != before) {
                return execute(req, allowCsrfRetry = false)
            }
        }
        throw apiError(code, body, message, req.url.toString())
    }

    private fun attachCsrf(req: Request): Request {
        if (req.header("X-CSRF-Token") != null) return req
        val token = csrfProvider() ?: return req
        return req.newBuilder().header("X-CSRF-Token", token).build()
    }

    private fun apiError(code: Int, body: String, message: String, url: String): ApiException {
        val kind = when {
            code == 403 && looksLikeCsrfFailure(body) -> ApiException.Kind.CSRF
            code == 401 || code == 403 -> ApiException.Kind.AUTH
            code == 429 -> ApiException.Kind.RATE_LIMIT
            code in 500..599 -> ApiException.Kind.SERVER
            else -> ApiException.Kind.UNKNOWN
        }
        return ApiException(kind, "HTTP $code $message", HttpException(code, body, url))
    }

    private fun looksLikeCsrfFailure(body: String): Boolean {
        val b = body.lowercase()
        return "invalid authenticity token" in b || "csrf" in b
    }

    /** 等待响应，返回 (body, code, message) */
    private suspend fun await(req: Request): Triple<String, Int, String> =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(c: Call, e: IOException) {
                    cont.resumeWithException(
                        ApiException(ApiException.Kind.NETWORK, e.message ?: "网络请求失败", e)
                    )
                }

                override fun onResponse(c: Call, response: Response) {
                    response.use { r ->
                        val text = try {
                            r.body?.string().orEmpty()
                        } catch (e: Exception) {
                            ""
                        }
                        cont.resume(Triple(text, r.code, r.message))
                    }
                }
            })
        }
}
