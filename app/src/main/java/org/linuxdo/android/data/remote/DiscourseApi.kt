package org.linuxdo.android.data.remote

import kotlinx.serialization.json.Json
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
    enum class Kind { NETWORK, AUTH, PARSE, RATE_LIMIT, SERVER, UNKNOWN }
}

/**
 * Discourse REST 封装：拼 URL、发请求、反序列化。业务语义留给 Repository。
 */
class DiscourseApi(
    private val baseUrlProvider: () -> String,
    private val clientProvider: () -> OkHttpClient,
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

    private suspend fun execute(req: Request): String =
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
                        if (r.isSuccessful) {
                            cont.resume(text)
                        } else {
                            val kind = when (r.code) {
                                401, 403 -> ApiException.Kind.AUTH
                                429 -> ApiException.Kind.RATE_LIMIT
                                in 500..599 -> ApiException.Kind.SERVER
                                else -> ApiException.Kind.UNKNOWN
                            }
                            cont.resumeWithException(
                                ApiException(
                                    kind,
                                    "HTTP ${r.code} ${r.message}",
                                    HttpException(r.code, text, req.url.toString())
                                )
                            )
                        }
                    }
                }
            })
        }
}
