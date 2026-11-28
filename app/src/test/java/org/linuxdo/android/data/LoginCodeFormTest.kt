package org.linuxdo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.linuxdo.android.data.model.HoneypotResponse

class LoginCodeFormTest {

    /** Discourse 前端会把 hp.json 的 challenge 反转后再提交，否则会被蜜罐判定为机器人 */
    @Test
    fun `login code form uses honeypot and reversed challenge`() {
        val form = loginCodeForm(
            email = "  user@example.com  ",
            honeypot = HoneypotResponse(value = "hp-value", challenge = "abc123"),
        )

        assertEquals("user@example.com", form["email"])
        assertEquals("false", form["signup"])
        assertEquals("hp-value", form["password_confirmation"])
        assertEquals("321cba", form["challenge"])
    }
}
