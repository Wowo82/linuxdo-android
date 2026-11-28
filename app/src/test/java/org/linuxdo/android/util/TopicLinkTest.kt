package org.linuxdo.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicLinkTest {

    @Test
    fun `plain absolute url`() {
        assertEquals(
            TopicLink.Target(12345, null),
            TopicLink.parse("https://linux.do/t/12345"),
        )
    }

    @Test
    fun `slug url`() {
        assertEquals(
            TopicLink.Target(12345, null),
            TopicLink.parse("https://linux.do/t/some-slug-here/12345"),
        )
    }

    @Test
    fun `slug url with post number`() {
        assertEquals(
            TopicLink.Target(12345, 7),
            TopicLink.parse("https://linux.do/t/some-slug/12345/7"),
        )
    }

    @Test
    fun `json suffix and query and fragment`() {
        assertEquals(
            TopicLink.Target(99, null),
            TopicLink.parse("https://linux.do/t/slug/99.json?u=foo"),
        )
        assertEquals(
            TopicLink.Target(99, 3),
            TopicLink.parse("https://linux.do/t/slug/99/3.json#post_3"),
        )
    }

    @Test
    fun `scheme-relative and no scheme`() {
        assertEquals(TopicLink.Target(7, null), TopicLink.parse("//linux.do/t/7"))
        assertEquals(TopicLink.Target(7, null), TopicLink.parse("linux.do/t/7"))
    }

    @Test
    fun `slug containing digits still resolves topic id`() {
        // slug 里带数字，topic id 是最后那段
        assertEquals(
            TopicLink.Target(2024, null),
            TopicLink.parse("https://linux.do/t/release-2024-notes/2024"),
        )
    }

    @Test
    fun `non topic paths are rejected`() {
        assertNull(TopicLink.parse("https://linux.do/u/someone"))
        assertNull(TopicLink.parse("https://linux.do/c/dev/12"))
        assertNull(TopicLink.parse("https://linux.do/"))
        assertNull(TopicLink.parse(null))
        assertNull(TopicLink.parse(""))
    }

    @Test
    fun `zero and negative ids are rejected`() {
        assertNull(TopicLink.parse("https://linux.do/t/0"))
        assertNull(TopicLink.parse("https://linux.do/t/slug/0/5"))
    }
}
