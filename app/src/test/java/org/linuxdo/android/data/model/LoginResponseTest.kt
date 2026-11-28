package org.linuxdo.android.data.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 密码正确但缺验证码时的响应：
     * {"ok":false,"error":"...","reason":"invalid_second_factor",...,"totp_enabled":true,...}
     */
    @Test
    fun `password second factor challenge is detected`() {
        val response = json.decodeFromString<LoginResponse>(
            """
            {
              "ok": false,
              "error": "Invalid authentication code",
              "reason": "invalid_second_factor",
              "totp_enabled": true,
              "backup_enabled": true,
              "security_key_enabled": false
            }
            """.trimIndent()
        )

        assertFalse(response.success)
        assertTrue(response.secondFactorRequired)
        assertTrue(response.totpEnabled)
        assertTrue(response.backupAvailable)
        assertFalse(response.securityKeyEnabled)
    }

    /** 邮箱验证码登录的 2FA 信息字段名不同：backup_codes_enabled / security_key_required */
    @Test
    fun `email code second factor info is detected`() {
        val response = json.decodeFromString<LoginResponse>(
            """
            {
              "second_factor_required": true,
              "totp_enabled": false,
              "backup_codes_enabled": true
            }
            """.trimIndent()
        )

        assertTrue(response.success)
        assertTrue(response.secondFactorRequired)
        assertFalse(response.totpEnabled)
        assertTrue(response.backupAvailable)
    }

    @Test
    fun `security key only challenge is detected`() {
        val response = json.decodeFromString<LoginResponse>(
            """{"second_factor_required":true,"security_key_required":true}"""
        )

        assertTrue(response.secondFactorRequired)
        assertTrue(response.securityKeyRequired)
        assertFalse(response.totpEnabled)
        assertFalse(response.backupAvailable)
    }

    @Test
    fun `captcha failure is detected`() {
        val response = json.decodeFromString<LoginResponse>(
            """{"error":"人机验证失败"}"""
        )

        assertFalse(response.success)
        assertTrue(response.captchaRequired)
        assertFalse(response.secondFactorRequired)
    }

    @Test
    fun `plain invalid credentials is not a second factor or captcha challenge`() {
        val response = json.decodeFromString<LoginResponse>(
            """{"error":"Invalid username/password","reason":"invalid_credentials"}"""
        )

        assertFalse(response.success)
        assertFalse(response.secondFactorRequired)
        assertFalse(response.captchaRequired)
    }

    @Test
    fun `pending approval is parsed`() {
        val response = json.decodeFromString<LoginResponse>("""{"pending_approval":true}""")

        assertTrue(response.pendingApproval)
    }
}
