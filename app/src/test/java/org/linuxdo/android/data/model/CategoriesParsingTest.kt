package org.linuxdo.android.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoriesParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** /categories.json 的根节点是 {"category_list": ...}，不能直接解码成 CategoryList。 */
    @Test
    fun `categories response is decoded from category_list wrapper`() {
        val raw = """
            {
              "category_list": {
                "can_create_topic": false,
                "can_create_category": false,
                "categories": [
                  {
                    "id": 1,
                    "name": "Parent",
                    "slug": "parent",
                    "color": "00AEEF",
                    "topic_count": 12,
                    "subcategory_list": [
                      {
                        "id": 2,
                        "name": "Child",
                        "slug": "child",
                        "color": "12A89D",
                        "topic_count": 3
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<CategoriesResponse>(raw)

        assertEquals(1, response.categoryList.categories.size)
        val parent = response.categoryList.categories.first()
        assertEquals("Parent", parent.name)
        assertEquals("parent", parent.slug)
        assertEquals(1, parent.subcategoryList?.size)
        assertEquals("Child", parent.subcategoryList?.first()?.name)
    }
}
