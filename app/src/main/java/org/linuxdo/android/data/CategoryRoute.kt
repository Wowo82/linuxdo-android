package org.linuxdo.android.data

/**
 * 分类话题流的路径拼接。
 *
 * Discourse 的规范路由是 `/c/{slug}/{id}/l/{filter}.json`，slug 可省。
 * 关键是不能在 slug 缺失时把 id 补到 slug 位上——那会拼出
 * `/c/12/12/l/latest.json` 这种重复段的坏 URL。
 *
 * 抽成纯函数是为了能在 JVM 上直接单测，不用起 mock server。
 */
object CategoryRoute {

    fun path(filter: String, categoryId: Long?, slug: String?): String = buildString {
        append('/')
        if (categoryId != null && filter != "categories") {
            append("c/")
            val cleanSlug = slug?.trim().orEmpty()
            if (cleanSlug.isNotEmpty()) append(cleanSlug).append('/')
            append(categoryId).append("/l/").append(filter)
        } else {
            append(filter)
        }
        append(".json")
    }
}
