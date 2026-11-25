package org.linuxdo.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.searchDataStore by preferencesDataStore("linuxdo.search")

/** 搜索历史：最近 20 条，新的在前。 */
class SearchHistoryStore(private val context: Context) {

    suspend fun add(term: String) {
        val t = term.trim()
        if (t.length < 2) return
        context.searchDataStore.edit { prefs ->
            val key = stringSetPreferencesKey("history")
            val old = prefs[key] ?: emptySet()
            prefs[key] = (listOf(t) + old.filter { it != t }).take(20).toSet()
        }
    }

    suspend fun list(): List<String> {
        val set = context.searchDataStore.data.first()[stringSetPreferencesKey("history")]
            ?: emptySet()
        return set.toList()
    }

    suspend fun clear() {
        context.searchDataStore.edit { it.remove(stringSetPreferencesKey("history")) }
    }
}
