package org.linuxdo.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 头像 / 图片地址必须跟着「我的 → 服务器地址」走。
 * 原来写死 `https://linux.do`，自建 Discourse 上头像全指向官方站。
 */
class SiteUrlTest {

    private val official = "https://linux.do"
    private val selfHost = "https://bbs.example.com"

    @Test
    fun `avatar template uses given site`() {
        assertEquals(
            "https://linux.do/user_avatar/linux.do/someone/96/12345_2.png",
            avatarUrl("/user_avatar/linux.do/someone/{size}/12345_2.png", 96, official),
        )
        assertEquals(
            "https://bbs.example.com/user_avatar/bbs.example.com/u/48/1_2.png",
            avatarUrl("/user_avatar/bbs.example.com/u/{size}/1_2.png", 48, selfHost),
        )
    }

    @Test
    fun `avatar protocol relative and absolute`() {
        assertEquals(
            "https://cdn.example.com/a/96/x.png",
            avatarUrl("//cdn.example.com/a/{size}/x.png", 96, selfHost),
        )
        assertEquals(
            "https://avatars.githubusercontent.com/u/1",
            avatarUrl("https://avatars.githubusercontent.com/u/1", 96, selfHost),
        )
    }

    @Test
    fun `avatar null or blank`() {
        assertNull(avatarUrl(null, 96, selfHost))
        assertNull(avatarUrl("   ", 96, selfHost))
    }

    @Test
    fun `absolutize keeps absolute urls`() {
        assertEquals("https://cdn/a.png", absolutize("https://cdn/a.png", selfHost))
        assertEquals("https://cdn/a.png", absolutize("//cdn/a.png", selfHost))
    }

    @Test
    fun `absolutize prefixes site paths`() {
        assertEquals("https://bbs.example.com/uploads/a.png", absolutize("/uploads/a.png", selfHost))
        assertEquals("https://bbs.example.com/uploads/a.png", absolutize("uploads/a.png", selfHost))
    }

    @Test
    fun `absolutize respects reverse-proxy subpath`() {
        assertEquals(
            "https://host/bbs/uploads/a.png",
            absolutize("/uploads/a.png", "https://host/bbs"),
        )
    }

    @Test
    fun `absolutize does not double slash`() {
        assertEquals("https://host/u/a.png", absolutize("/u/a.png", "https://host/"))
    }
}
