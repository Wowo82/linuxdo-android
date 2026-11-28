package org.linuxdo.android.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * CSRF 链路回归测试。
 *
 * Discourse 对 browser session 的非 GET 请求强制校验 `X-CSRF-Token`，
 * 原来只带 cookie + X-Requested-With，读一切正常、一写就 403。
 */
class CsrfTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiWith(tokens: CsrfTokens): DiscourseApi {
        val base = server.url("/").toString().trimEnd('/')
        val client = OkHttpClient.Builder()
            .addInterceptor(HeaderInterceptor(base, tokens))
            .build()
        return DiscourseApi(
            baseUrlProvider = { base },
            clientProvider = { client },
            csrfProvider = { tokens.current() },
            csrfPersist = { tokens.update(it) },
        )
    }

    @Test
    fun `write requests carry csrf token and origin`() = runTest {
        val tokens = CsrfTokens(load = { "TOKEN_A" }, save = {})
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        apiWith(tokens).postForm("/posts", mapOf("raw" to "hi"))

        val req = server.takeRequest()
        assertEquals("TOKEN_A", req.getHeader("X-CSRF-Token"))
        assertEquals(server.url("/").toString().trimEnd('/'), req.getHeader("Origin"))
        assertEquals("XMLHttpRequest", req.getHeader("X-Requested-With"))
    }

    @Test
    fun `read requests never carry csrf token`() = runTest {
        val tokens = CsrfTokens(load = { "TOKEN_A" }, save = {})
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        apiWith(tokens).getRaw("/latest.json")

        val req = server.takeRequest()
        assertNull(req.getHeader("X-CSRF-Token"))
        assertEquals("true", req.getHeader("Discourse-Logged-In"))
    }

    @Test
    fun `403 csrf failure refreshes token and replays once`() = runTest {
        val tokens = CsrfTokens(load = { "STALE" }, save = {})
        // 1. 首次写：token 过期
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"errors":["Invalid Authenticity Token"]}""")
        )
        // 2. 取新 token
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"csrf":"FRESH"}"""))
        // 3. 重放成功
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"id":42}"""))

        val result = apiWith(tokens).postForm("/posts", mapOf("raw" to "hi"))

        assertEquals("""{"id":42}""", result)
        assertEquals("FRESH", tokens.current())

        assertEquals("STALE", server.takeRequest().getHeader("X-CSRF-Token"))
        assertEquals("/session/csrf.json", server.takeRequest().path)
        assertEquals("FRESH", server.takeRequest().getHeader("X-CSRF-Token"))
    }

    @Test
    fun `unchanged token does not loop`() = runTest {
        val tokens = CsrfTokens(load = { "SAME" }, save = {})
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"errors":["Invalid Authenticity Token"]}""")
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"csrf":"SAME"}"""))

        val err = runCatching { apiWith(tokens).postForm("/posts", mapOf("raw" to "hi")) }
            .exceptionOrNull()

        assertTrue(err is ApiException)
        assertEquals(ApiException.Kind.CSRF, (err as ApiException).kind)
        // 原请求 + 取 token，共 2 次；因为 token 没变，不再重放
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `plain permission 403 is not treated as csrf`() = runTest {
        val tokens = CsrfTokens(load = { "OK" }, save = {})
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"errors":["您没有权限"]}""")
        )

        val err = runCatching { apiWith(tokens).postForm("/posts", mapOf("raw" to "hi")) }
            .exceptionOrNull()

        assertEquals(ApiException.Kind.AUTH, (err as ApiException).kind)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `no token stored means no header but origin still set`() = runTest {
        val tokens = CsrfTokens(load = { null }, save = {})
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        apiWith(tokens).postForm("/posts", mapOf("raw" to "hi"))

        val req = server.takeRequest()
        assertNull(req.getHeader("X-CSRF-Token"))
        assertEquals(server.url("/").toString().trimEnd('/'), req.getHeader("Origin"))
    }
}
