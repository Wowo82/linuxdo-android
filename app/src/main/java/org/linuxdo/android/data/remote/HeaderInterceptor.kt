package org.linuxdo.android.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import org.linuxdo.android.BuildConfig

/**
 * 统一补齐 Discourse 需要的请求头。
 *
 * - `X-Requested-With: XMLHttpRequest` → 让站点返回 JSON 而不是重定向到 HTML 登录页
 * - `Accept: application/json`
 * - `Discourse-Logged-In: true` → 站点据此跳过匿名静态缓存
 * - 写操作补 Origin / Referer，Discourse 会校验（防 CSRF）
 * - UA 带客户端版本，方便服务端区分流量
 */
class HeaderInterceptor(private val baseUrl: String) : Interceptor {

    private val origin: String = baseUrl.trimEnd('/')

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val b = req.newBuilder()

        if (req.header("Accept") == null) b.header("Accept", "application/json")
        if (req.header("X-Requested-With") == null) {
            b.header("X-Requested-With", "XMLHttpRequest")
        }
        if (req.header("Discourse-Logged-In") == null) {
            b.header("Discourse-Logged-In", "true")
        }
        if (req.header("User-Agent") == null) b.header("User-Agent", userAgent())

        val needsCsrfHeaders = req.method != "GET" && req.method != "HEAD"
        if (needsCsrfHeaders) {
            if (req.header("Origin") == null) b.header("Origin", origin)
            if (req.header("Referer") == null) b.header("Referer", "$origin/")
        }

        return chain.proceed(b.build())
    }

    private fun userAgent(): String {
        val release = android.os.Build.VERSION.RELEASE ?: "?"
        val model = android.os.Build.MODEL ?: "Android"
        return "Mozilla/5.0 (Linux; Android $release; $model) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "LDAndroid/${BuildConfig.VERSION_NAME} Chrome/131.0 Mobile Safari/537.36"
    }
}
