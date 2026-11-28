package org.linuxdo.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlTest {

    /**
     * 超出 Unicode 范围的数字实体（> 0x10FFFF）在 Character.toChars 里会抛
     * IllegalArgumentException，导致列表页在渲染摘要时崩溃。
     */
    @Test
    fun `out of range decimal entity is ignored`() {
        assertEquals("", Html.unescape("&#1114112;"))
        assertEquals("", Html.unescape("&#9999999;"))
    }

    @Test
    fun `out of range hex entity is ignored`() {
        assertEquals("", Html.unescape("&#x110000;"))
        assertEquals("", Html.unescape("&#x10FFFFF;"))
    }

    @Test
    fun `valid numeric entities still decode`() {
        assertEquals("A", Html.unescape("&#65;"))
        assertEquals("\uD83D\uDE00", Html.unescape("&#x1F600;"))
    }

    @Test
    fun `toPlain does not crash on out of range entity in excerpt`() {
        assertEquals("hi there", Html.toPlain("<p>hi &#1114112; there</p>"))
        assertEquals("hi there", Html.toPlain("<p>hi &amp;#1114112; there</p>"))
    }
}
