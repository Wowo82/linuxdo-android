package org.linuxdo.android.util

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/** Discourse 的时间全是 ISO8601 UTC，这里统一转成「刚刚 / 5 分钟前 / 3 天前 / 2026-11-20」 */
object TimeFmt {

    fun relative(iso: String?): String {
        val instant = parse(iso) ?: return ""
        val now = Instant.now()
        val d = Duration.between(instant, now)
        val secs = d.seconds
        return when {
            secs < 0 -> "刚刚"
            secs < 60 -> "刚刚"
            secs < 3600 -> "${secs / 60} 分钟前"
            secs < 86_400 -> "${secs / 3600} 小时前"
            secs < 86_400 * 30 -> "${secs / 86_400} 天前"
            secs < 86_400 * 365 -> "${secs / (86_400 * 30)} 个月前"
            else -> "${secs / (86_400 * 365)} 年前"
        }
    }

    fun absolute(iso: String?): String {
        val instant = parse(iso) ?: return ""
        val dt = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        return "%04d-%02d-%02d %02d:%02d".format(
            dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute
        )
    }

    fun dateOnly(iso: String?): String {
        val instant = parse(iso) ?: return ""
        val dt = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return "%04d-%02d-%02d".format(dt.year, dt.monthValue, dt.dayOfMonth)
    }

    private fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (e: DateTimeParseException) {
            runCatching { Instant.parse(iso) }.getOrNull()
        }
    }
}

/** 数字缩写：1234 → 1.2k */
fun Int.shortCount(): String = when {
    this < 1000 -> toString()
    this < 1_000_000 -> String.format("%.1fk", this / 1000.0).replace(".0k", "k")
    else -> String.format("%.1fm", this / 1_000_000.0).replace(".0m", "m")
}
