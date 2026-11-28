package org.linuxdo.android.data.remote

import okhttp3.Cookie

/**
 * 把 WebView `CookieManager.getCookie()` 返回的 `"a=b; c=d"` 解析成 OkHttp Cookie。
 *
 * WebView 已经按 URL 筛过 cookie，但不暴露 Path / Domain / Expires：
 * 统一落到当前站点根路径；不设置 expiresAt 即会话 cookie（仍会被加密持久化）。
 * 账号会话的 `_t` 是 HttpOnly，CookieManager 的应用层 API 能读到。
 */
fun parseCookieHeader(header: String?, host: String): List<Cookie> {
    if (header.isNullOrBlank() || host.isBlank()) return emptyList()
    return header.split(';').mapNotNull { part ->
        val raw = part.trim()
        val eq = raw.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        val name = raw.substring(0, eq).trim()
        val value = raw.substring(eq + 1).trim()
        if (name.isEmpty()) return@mapNotNull null
        runCatching {
            Cookie.Builder()
                .name(name)
                .value(value)
                .domain(host)
                .path("/")
                .build()
        }.getOrNull()
    }
}
