package org.linuxdo.android.data.remote

/**
 * CSRF token 的进程内缓存 + 落盘回调。
 *
 * Discourse 对**非 GET** 请求校验 `X-CSRF-Token`（browser session 走这套）。
 * 只带 cookie + `X-Requested-With` 不足以豁免：读请求一切正常，一写就
 * `403 Invalid Authenticity Token`。
 *
 * token 跟着会话走，所以和 cookie 一起存在加密 prefs 里。
 */
class CsrfTokens(
    private val load: () -> String?,
    private val save: (String?) -> Unit,
) {
    @Volatile
    private var cached: String? = load()?.takeIf { it.isNotBlank() }

    fun current(): String? = cached

    fun update(token: String?) {
        val clean = token?.trim()?.takeIf { it.isNotEmpty() }
        cached = clean
        save(clean)
    }

    fun clear() = update(null)

    val hasToken: Boolean get() = cached != null
}
