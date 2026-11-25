package org.linuxdo.android.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 内存 + 持久化的 CookieJar。
 *
 * Discourse 的会话是 `_t` cookie（HttpOnly），只能靠 cookie jar 维持。
 * 变更时全量回调给上层落盘（SessionStore 用 EncryptedSharedPreferences 存）。
 */
class PersistentCookieJar(
    private val load: () -> List<Cookie>,
    private val save: (List<Cookie>) -> Unit,
) : CookieJar {

    // key = "domain|path|name"，同名新 cookie 覆盖旧的
    private val store = LinkedHashMap<String, Cookie>()

    init {
        load().forEach { store[keyOf(it)] = it }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        var changed = false
        val now = System.currentTimeMillis()
        for (c in cookies) {
            if (c.expiresAt < now) {
                // 过期 cookie 视为删除
                if (store.remove(keyOf(c)) != null) changed = true
            } else {
                store[keyOf(c)] = c
                changed = true
            }
        }
        if (changed) save(store.values.toList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store.values.filter { it.matches(url) && it.expiresAt > now }
    }

    fun clear() {
        store.clear()
        save(emptyList())
    }

    fun snapshot(): List<Cookie> = store.values.toList()

    private fun keyOf(c: Cookie): String = "${c.domain}|${c.path}|${c.name}"
}
