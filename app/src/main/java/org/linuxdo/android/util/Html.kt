package org.linuxdo.android.util

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
    private val LINE_PAD = Regex(" *\\n *")
    private val DEC_ENTITY = Regex("&#(\\d{1,8});")
    private val HEX_ENTITY = Regex("&#[xX]([0-9a-fA-F]{1,8});")

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
        // 行首行尾的残留空格会变成「a\n b」这种参差排版
        s = s.replace(LINE_PAD, "\n")
        s = s.replace(Regex("\\n{3,}"), "\n\n")
        return s.trim()
    }

    /**
     * 解 HTML 实体。
     *
     * 顺序很关键：数字实体必须在 `&amp;` 之前解，否则 `&amp;#1114112;` 会在
     * 解完 `&amp;` 之后变成真实体，再交给 `Character.toChars` 就越界抛
     * IllegalArgumentException（列表摘要直接崩）。
     * 这里先解数字实体，再把 `&amp;` 留到最后，最后再扫一遍把**残留的**
     * 越界数字实体字面量丢掉——既保留 HTML 语义（`&amp;#65;` 应当显示成
     * `&#65;`），又不会被畸形输入炸掉。
     */
    fun unescape(s: String): String {
        var out = decodeNumericEntities(s)
        out = out
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
        return stripInvalidNumericEntities(out)
    }

    /** 合法码点就地解码；越界的一律丢空，绝不抛异常 */
    private fun decodeNumericEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        var out = DEC_ENTITY.replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull() ?: return@replace ""
            codePointToString(cp)
        }
        out = HEX_ENTITY.replace(out) { m ->
            val cp = m.groupValues[1].toIntOrNull(16) ?: return@replace ""
            codePointToString(cp)
        }
        return out
    }

    /** 只清掉解 `&amp;` 之后新冒出来的非法数字实体，合法的保持字面量 */
    private fun stripInvalidNumericEntities(s: String): String {
        if (s.indexOf('&') < 0) return s
        var out = DEC_ENTITY.replace(s) { m ->
            val cp = m.groupValues[1].toIntOrNull()
            if (cp != null && Character.isValidCodePoint(cp)) m.value else ""
        }
        out = HEX_ENTITY.replace(out) { m ->
            val cp = m.groupValues[1].toIntOrNull(16)
            if (cp != null && Character.isValidCodePoint(cp)) m.value else ""
        }
        return out
    }

    private fun codePointToString(cp: Int): String =
        if (Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else ""

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

/**
 * 从 Discourse 的 avatar_template 造指定尺寸的头像 URL。
 *
 * 模板有四种形态，都要落到**当前站点**，不能写死 linux.do——
 * 否则「我的 → 服务器地址」改成自建 Discourse 后头像全指向官方站。
 *  - `/user_avatar/linux.do/someone/{size}/12345_2.png`   站内相对路径
 *  - `//cdn.example.com/user_avatar/.../{size}/...`       协议相对
 *  - `https://cdn.example.com/.../{size}/...`             绝对
 */
fun avatarUrl(template: String?, size: Int, baseUrl: String): String? {
    if (template.isNullOrBlank()) return null
    val sized = template.replace("{size}", size.toString())
    return toAbsoluteUrl(sized, baseUrl)
}

/** 把相对图片 / 上传地址补成绝对地址，站点地址由调用方传入 */
fun absolutize(url: String?, baseUrl: String): String? {
    if (url.isNullOrBlank()) return null
    return toAbsoluteUrl(url, baseUrl)
}

private fun toAbsoluteUrl(raw: String, baseUrl: String): String? {
    val url = raw.trim()
    if (url.isEmpty()) return null
    return when {
        url.startsWith("http://", true) || url.startsWith("https://", true) -> url
        url.startsWith("//") -> "https:$url"
        // 站内绝对路径：拼当前站点，保留站点自带的子路径（如反向代理挂在 /bbs 下）
        url.startsWith("/") -> {
            val base = baseUrl.trimEnd('/')
            if (base.isEmpty()) url else base + url
        }
        // 相对路径（无前导斜杠）：按站点根处理，避免把 base 当目录拼错
        else -> {
            val base = baseUrl.trimEnd('/')
            if (base.isEmpty()) url else "$base/$url"
        }
    }
}
