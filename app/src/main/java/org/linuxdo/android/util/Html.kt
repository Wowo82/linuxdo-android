package org.linuxdo.android.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Discourse 返回的两类富文本：
 *  - `cooked`  已渲染 HTML（绝大多数场景用这个）
 *  - `raw`     原始 BBcode/Markdown
 *
 * 这里只做「预览 / 摘要」需要的轻量处理，不做完整 HTML 解析。
 */
object Html {

    private val TAG = Regex("<[^>]+>")
    private val WS = Regex("[ \\t\\u00A0]+")

    /** 去标签 → 解实体 → 压空白 */
    fun toPlain(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var s = html
        s = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</p>"), "\n")
        s = s.replace(TAG, " ")
        s = unescape(s)
        s = s.replace(WS, " ")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }

    fun unescape(s: String): String = s
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()
                ?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) } ?: ""
        }
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)
                ?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) } ?: ""
        }

    /** 抽第一张图，用作列表缩略图 */
    fun firstImageSrc(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
    }

    /** 判断纯文本里有没有中文，决定要不要禁用断词 */
    fun isCjkDominant(text: String): Boolean {
        if (text.isEmpty()) return false
        var cjk = 0
        var total = 0
        for (c in text) {
            if (c.isWhitespace()) continue
            total++
            if (c.code in 0x4E00..0x9FFF || c.code in 0x3040..0x30FF || c.code in 0xAC00..0xD7AF) {
                cjk++
            }
        }
        return total > 0 && cjk.toFloat() / total > 0.3f
    }
}

/** 从 Discourse 的 avatar_template 造指定尺寸的头像 URL */
fun avatarUrl(template: String?, size: Int = 96): String? {
    if (template.isNullOrBlank()) return null
    val cleaned = Regex("\\{size\\}")
    val url = template.replace("{size}", size.toString())
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://linux.do$url"
        else -> url
    }
}

/** 把相对图片 / 上传地址补成绝对地址 */
fun absolutize(url: String?, base: String = "https://linux.do"): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> base.trimEnd('/') + url
        else -> url
    }
}
