package org.linuxdo.android.util

/**
 * Discourse 话题链接解析。
 *
 * 纯字符串实现（不依赖 android.net.Uri），这样能在 JVM 单元测试里直接跑。
 *
 * 支持这些形态：
 *  - `/t/12345`
 *  - `/t/12345/7`                      无 slug，带楼层
 *  - `/t/some-slug/12345`
 *  - `/t/some-slug/12345/7`            带楼层
 *  - `/t/12345.json`、`/t/slug/12345/7.json`
 *  - `https://linux.do/t/slug/12345?u=foo#post_7`
 *  - `//linux.do/t/12345`、`linux.do/t/12345`   省略 scheme
 *
 * 只认 `/t/` 段，其他路径返回 null，交给系统浏览器处理。
 */
object TopicLink {

    data class Target(val topicId: Long, val postNumber: Int? = null)

    fun parse(url: String?): Target? {
        if (url.isNullOrBlank()) return null
        return parsePath(pathOf(url))
    }

    /** 去掉 scheme / host / query / fragment，只留下路径部分 */
    private fun pathOf(url: String): String {
        var s = url.trim()

        val schemeEnd = s.indexOf("://")
        if (schemeEnd >= 0) {
            // scheme://host/path
            val afterScheme = s.substring(schemeEnd + 3)
            val slash = afterScheme.indexOf('/')
            s = if (slash >= 0) afterScheme.substring(slash) else "/"
        } else if (s.startsWith("//")) {
            // 协议相对：//host/path
            val afterSlashes = s.substring(2)
            val slash = afterSlashes.indexOf('/')
            s = if (slash >= 0) afterSlashes.substring(slash) else "/"
        } else if (!s.startsWith("/")) {
            // 没有 scheme 但带主机名：第一段含 . 或 : 就当主机丢掉
            val firstSlash = s.indexOf('/')
            val head = if (firstSlash >= 0) s.substring(0, firstSlash) else s
            if ('.' in head || ':' in head) {
                s = if (firstSlash >= 0) s.substring(firstSlash) else "/"
            }
        }

        s = s.substringBefore('?').substringBefore('#')
        return s.ifEmpty { "/" }
    }

    fun parsePath(path: String?): Target? {
        if (path.isNullOrBlank()) return null
        val clean = path.substringBefore('?').substringBefore('#').trim('/')
        if (clean.isEmpty()) return null

        val seg = clean.split('/')
        if (seg.size < 2 || !seg[0].equals("t", ignoreCase = true)) return null

        // 去掉 .json 后缀后逐段判断
        val parts = seg.drop(1).map { it.substringBefore('.') }
        if (parts.isEmpty()) return null

        fun numeric(i: Int): Long? = parts.getOrNull(i)?.toLongOrNull()?.takeIf { it > 0 }

        // 按 Discourse 的固定形态定位 topic id：
        //   1 段       /t/{id}
        //   2 段       /t/{id}/{post}  或  /t/{slug}/{id}
        //   3 段及以上 /t/{slug}/{id}/{post}
        val topicId: Long
        val postNumber: Int?
        when {
            parts.size == 1 -> {
                topicId = numeric(0) ?: return null
                postNumber = null
            }
            parts.size == 2 -> {
                val a = numeric(0)
                val b = numeric(1)
                when {
                    // 两段都是数字：无 slug 的 /t/{id}/{post}
                    a != null && b != null -> {
                        topicId = a
                        postNumber = b.toInt().takeIf { it > 0 }
                    }
                    b != null -> {
                        topicId = b
                        postNumber = null
                    }
                    a != null -> {
                        topicId = a
                        postNumber = null
                    }
                    else -> return null
                }
            }
            else -> {
                topicId = numeric(1) ?: return null
                postNumber = numeric(2)?.toInt()?.takeIf { it > 0 }
            }
        }
        if (topicId <= 0) return null
        return Target(topicId, postNumber)
    }
}
