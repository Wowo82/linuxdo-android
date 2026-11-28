package org.linuxdo.android.data.remote

import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OkHttpClient 工厂。
 *
 * 切站点要换 HeaderInterceptor（Origin/Referer 变了），所以按 baseUrl 缓存客户端；
 * 图片客户端不挂 cookie、单独连接池，避免和 API 抢连接。
 */
object HttpClients {

    private val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .dns(Doh.dns())
            .build()
    }

    private val cache = ConcurrentHashMap<String, OkHttpClient>()

    fun forSite(baseUrl: String, cookieJar: CookieJar, csrfTokens: CsrfTokens): OkHttpClient =
        cache.getOrPut(baseUrl) {
            base.newBuilder()
                .cookieJar(cookieJar)
                .addInterceptor(HeaderInterceptor(baseUrl, csrfTokens))
                .build()
        }

    /** 切站点后必须调用，否则旧站点的 Origin/Referer 与 CSRF 缓存会跟着复用 */
    fun invalidate() = cache.clear()
}
