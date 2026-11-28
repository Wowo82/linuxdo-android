package org.linuxdo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分类话题流的路径拼接。
 *
 * Discourse 的规范路由是 `/c/{slug}/{id}/l/{filter}.json`，slug 可省。
 * 关键是不能在 slug 缺失时把 id 补到 slug 位上——那会拼出
 * `/c/12/12/l/latest.json` 这种重复段的坏 URL。
 */
class CategoryRouteTest {

    private fun path(filter: String, categoryId: Long?, slug: String?): String =
        CategoryRoute.path(filter, categoryId, slug)

    @Test
    fun `slug and id form canonical route`() {
        assertEquals("/c/dev/12/l/latest.json", path("latest", 12, "dev"))
        assertEquals("/c/dev/12/l/top.json", path("top", 12, "dev"))
    }

    @Test
    fun `missing slug falls back to id only`() {
        assertEquals("/c/12/l/latest.json", path("latest", 12, null))
        assertEquals("/c/12/l/latest.json", path("latest", 12, ""))
        assertEquals("/c/12/l/latest.json", path("latest", 12, "   "))
    }

    @Test
    fun `no category means plain filter route`() {
        assertEquals("/latest.json", path("latest", null, "dev"))
        assertEquals("/categories.json", path("categories", 12, "dev"))
    }
}
