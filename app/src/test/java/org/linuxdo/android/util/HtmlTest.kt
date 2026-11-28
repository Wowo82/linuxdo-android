package org.linuxdo.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlTest {

    @Test
    fun `valid numeric entities decode`() {
        assertEquals("A", Html.unescape("&#65;"))
        assertEquals("A", Html.unescape("&#x41;"))
        assertEquals("\uD83D\uDE00", Html.unescape("&#x1F600;"))
    }

    @Test
    fun `out of range numeric entity is dropped`() {
        assertEquals("", Html.unescape("&#1114112;"))
        assertEquals("", Html.unescape("&#9999999;"))
        assertEquals("", Html.unescape("&#x110000;"))
    }

    @Test
    fun `double escaped out of range entity does not throw`() {
        // &amp;#1114112; 解完 &amp; 后会变成真实体，必须仍然被清掉而不是抛异常
        assertEquals("", Html.unescape("&amp;#1114112;"))
        assertEquals("", Html.unescape("&amp;#x110000;"))
    }

    @Test
    fun `double escaped valid entity keeps literal`() {
        // &amp;#65; 语义上就是字面量 "&#65;"，不能再解一次成 "A"
        assertEquals("&#65;", Html.unescape("&amp;#65;"))
    }

    @Test
    fun `named entities decode`() {
        assertEquals("<a & b>", Html.unescape("&lt;a &amp; b&gt;"))
        assertEquals("\"q\"", Html.unescape("&quot;q&quot;"))
        assertEquals("a b", Html.unescape("a&nbsp;b"))
    }

    @Test
    fun `toPlain does not crash on out of range entity`() {
        assertEquals("hi there", Html.toPlain("<p>hi &#1114112; there</p>"))
        assertEquals("hi there", Html.toPlain("<p>hi &amp;#1114112; there</p>"))
    }

    @Test
    fun `toPlain strips tags and collapses blank lines`() {
        assertEquals("a\nb", Html.toPlain("<p>a</p><p>b</p>"))
        assertEquals("code", Html.toPlain("<pre><code>code</code></pre>"))
        assertEquals("", Html.toPlain("<script>alert(1)</script>"))
    }


    @Test
    fun `firstImageSrc picks first img`() {
        assertEquals(
            "https://cdn/x.png",
            Html.firstImageSrc("<p><img src=\"https://cdn/x.png\" width=\"10\"></p>"),
        )
        assertNull(Html.firstImageSrc("<p>no image</p>"))
    }
}
