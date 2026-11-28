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
     * Discourse 密码正确但缺验证码时的响应：
     * {"ok":false,"error":"...","reason":"invalid_second_factor",...,"totp_enabled":true,...}
     */
    @Test
    fun `totp challenge is detected`() {
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
        assertTrue(response.backupEnabled)
        assertFalse(response.securityKeyEnabled)
    }

    @Test
    fun `security key only account is still a second factor challenge`() {
        val response = json.decodeFromString<LoginResponse>(
            """
            {
              "error": "需要两步验证",
              "reason": "invalid_second_factor_method",
              "totp_enabled": false,
              "backup_enabled": false,
              "security_key_enabled": true
            }
            """.trimIndent()
        )

        assertTrue(response.secondFactorRequired)
        assertTrue(response.securityKeyEnabled)
        assertFalse(response.totpEnabled)
    }

    @Test
    fun `plain invalid credentials is not a second factor challenge`() {
        val response = json.decodeFromString<LoginResponse>(
            """{"error":"Invalid username/password","reason":"invalid_credentials"}"""
        )

        assertFalse(response.success)
        assertFalse(response.secondFactorRequired)
    }
}
