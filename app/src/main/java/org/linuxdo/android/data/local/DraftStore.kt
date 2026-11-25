package org.linuxdo.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val Context.draftDataStore by preferencesDataStore("linuxdo.drafts")

/**
 * 草稿 + 阅读位置。
 *
 * 和 iOS 版一样按 key 存：`topic_123`（回复草稿）、`new_topic_5`（发新帖 + 分类 id）。
 * 只是本地缓存，服务端 /drafts.json 才是权威。
 */
class DraftStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Entry(val text: String, val updatedAt: Long)

    suspend fun save(key: String, text: String) {
        context.draftDataStore.edit { prefs ->
            prefs[stringPreferencesKey(key)] = "$text\u0001${System.currentTimeMillis()}"
        }
    }

    suspend fun load(key: String): Entry? =
        context.draftDataStore.data.first()[stringPreferencesKey(key)].let { raw ->
            if (raw.isNullOrBlank()) null
            else {
                val idx = raw.lastIndexOf('\u0001')
                if (idx < 0) Entry(raw, 0L)
                else Entry(raw.substring(0, idx), raw.substring(idx + 1).toLongOrNull() ?: 0L)
            }
        }

    suspend fun delete(key: String) {
        context.draftDataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    suspend fun allKeys(): List<String> =
        context.draftDataStore.data.first().asMap().keys.map { it.name }

    // ---------- 阅读位置 ----------

    suspend fun saveLastRead(topicId: Long, postNumber: Int) {
        context.draftDataStore.edit { prefs ->
            prefs[stringPreferencesKey("read_$topicId")] = postNumber.toString()
        }
    }

    suspend fun lastRead(topicId: Long): Int? =
        context.draftDataStore.data.first()[stringPreferencesKey("read_$topicId")]?.toIntOrNull()

    // ---------- 会话缓存的站点信息 ----------

    suspend fun cacheSiteBlob(blob: String) {
        context.draftDataStore.edit { it[stringPreferencesKey("site_blob")] = blob }
    }

    suspend fun siteBlob(): String? =
        context.draftDataStore.data.first()[stringPreferencesKey("site_blob")]
}
