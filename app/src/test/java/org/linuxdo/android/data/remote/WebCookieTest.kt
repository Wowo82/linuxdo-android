package org.linuxdo.android.data.remote

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网页登录后 cookie 从 WebView 同步进 OkHttp jar 的链路。
 * CookieManager 只给 "name=value; name2=value2"，没有 Path/Domain/Expires。
 */
class WebCookieTest {

    private val site = "https://linux.do".toHttpUrl()

    @Test
    fun `parse cookie header`() {
        val cookies = parseCookieHeader("_t=abc123; _forum_session=xyz", "linux.do")

        assertEquals(2, cookies.size)
        assertEquals("abc123", cookies.first { it.name == "_t" }.value)
        assertEquals("xyz", cookies.first { it.name == "_forum_session" }.value)
        assertTrue(cookies.all { it.domain == "linux.do" && it.path == "/" })
    }

    @Test
    fun `cookie value containing equals is preserved`() {
        val cookie = parseCookieHeader("token=a=b=c", "linux.do").single()
        assertEquals("a=b=c", cookie.value)
    }

    @Test
    fun `malformed segments are skipped`() {
        val cookies = parseCookieHeader("; _t=ok; broken; =noname; empty=", "linux.do")
        assertEquals(listOf("_t", "empty"), cookies.map { it.name })
    }

    @Test
    fun `putAll persists and loadForRequest returns cookies`() {
        val saves = mutableListOf<List<Cookie>>()
        val jar = PersistentCookieJar(load = { emptyList() }, save = { saves += it })
        val cookie = Cookie.Builder()
            .name("_t")
            .value("session-token")
            .domain("linux.do")
            .path("/")
            .build()

        jar.putAll(listOf(cookie))

        assertEquals("session-token", jar.loadForRequest(site).single().value)
        assertEquals(1, saves.size)
        assertEquals(1, saves.first().size)
    }

    @Test
    fun `putAll replaces same name cookie`() {
        val jar = PersistentCookieJar(load = { emptyList() }, save = {})
        fun cookie(value: String) = Cookie.Builder()
            .name("_t")
            .value(value)
            .domain("linux.do")
            .path("/")
            .build()

        jar.putAll(listOf(cookie("old")))
        jar.putAll(listOf(cookie("new")))

        assertEquals("new", jar.loadForRequest(site).single().value)
    }

    @Test
    fun `empty input does not persist`() {
        val saves = mutableListOf<List<Cookie>>()
        val jar = PersistentCookieJar(load = { emptyList() }, save = { saves += it })

        jar.putAll(emptyList())

        assertTrue(saves.isEmpty())
    }
}
