package org.linuxdo.android.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import org.linuxdo.android.data.remote.CsrfTokens
import org.linuxdo.android.data.remote.PersistentCookieJar

/**
 * 站点地址 + 登录态（cookie + CSRF token）持久化。
 *
 * cookie 里含 `_t` 会话令牌，CSRF token 能直接代替会话发起写操作，
 * 两者都落到 EncryptedSharedPreferences；
 * 只有 cookie jar / csrf 缓存初始化前会同步读一次。
 */
class SessionStore(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("linuxdo.prefs", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "linuxdo.secrets",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var baseUrl: String
        get() = plain.getString(KEY_BASE_URL, org.linuxdo.android.BuildConfig.DEFAULT_BASE_URL)
            ?.trimEnd('/')
            ?.takeIf { it.isNotBlank() }
            ?: org.linuxdo.android.BuildConfig.DEFAULT_BASE_URL
        set(value) {
            plain.edit().putString(KEY_BASE_URL, value.trimEnd('/')).apply()
        }

    var username: String?
        get() = plain.getString(KEY_USERNAME, null)
        set(value) = plain.edit().putString(KEY_USERNAME, value).apply()

    var userId: Long
        get() = plain.getLong(KEY_USER_ID, -1L)
        set(value) = plain.edit().putLong(KEY_USER_ID, value).apply()

    /** 站点图标 / 标题缓存 */
    var siteTitle: String?
        get() = plain.getString(KEY_SITE_TITLE, null)
        set(value) = plain.edit().putString(KEY_SITE_TITLE, value).apply()

    var siteIconUrl: String?
        get() = plain.getString(KEY_SITE_ICON, null)
        set(value) = plain.edit().putString(KEY_SITE_ICON, value).apply()

    val isLoggedIn: Boolean get() = username != null

    // ---------- cookie ----------

    val cookieJar: PersistentCookieJar by lazy {
        PersistentCookieJar(
            load = { decodeCookies(secure.getString(KEY_COOKIES, null)) },
            save = { list -> secure.edit().putString(KEY_COOKIES, encodeCookies(list)).apply() },
        )
    }

    // ---------- CSRF ----------

    val csrfTokens: CsrfTokens by lazy {
        CsrfTokens(
            load = { secure.getString(KEY_CSRF, null) },
            save = { token ->
                if (token == null) secure.edit().remove(KEY_CSRF).apply()
                else secure.edit().putString(KEY_CSRF, token).apply()
            },
        )
    }

    fun clearCookies() {
        cookieJar.clear()
        csrfTokens.clear()
        secure.edit().remove(KEY_COOKIES).remove(KEY_CSRF).apply()
        plain.edit().remove(KEY_USERNAME).remove(KEY_USER_ID).apply()
    }

    private fun encodeCookies(cookies: List<Cookie>): String =
        cookies.joinToString("\n") { c ->
            listOf(
                c.name, c.value, c.domain, c.path,
                c.expiresAt.toString(), c.secure.toString(), c.httpOnly.toString(),
            ).joinToString("\u0001")
        }

    private fun decodeCookies(raw: String?): List<Cookie> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val p = line.split('\u0001')
            if (p.size < 7) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(p[0])
                    .value(p[1])
                    .domain(p[2])
                    .path(p[3])
                    .expiresAt(p[4].toLong())
                    .apply { if (p[5].toBoolean()) secure() }
                    .apply { if (p[6].toBoolean()) httpOnly() }
                    .build()
            }.getOrNull()
        }.toList()
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_USERNAME = "username"
        const val KEY_USER_ID = "user_id"
        const val KEY_SITE_TITLE = "site_title"
        const val KEY_SITE_ICON = "site_icon"
        const val KEY_COOKIES = "cookies_v1"
        const val KEY_CSRF = "csrf_v1"
    }
}
