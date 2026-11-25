package org.linuxdo.android.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.data.UserRepository
import org.linuxdo.android.data.local.SearchHistoryStore
import org.linuxdo.android.data.model.SearchPost
import org.linuxdo.android.data.model.Topic

data class SearchState(
    val query: String = "",
    val results: List<SearchPost> = emptyList(),
    val topics: Map<Long, Topic> = emptyMap(),
    val history: List<String> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(
    private val repo: UserRepository,
    private val historyStore: SearchHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(history = historyStore.list()) }
        }
    }

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q) }
        // 300ms 防抖，够用且不打爆站点限速
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searched = false, error = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            run(q)
        }
    }

    fun submit(q: String = _state.value.query) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { run(q) }
    }

    private suspend fun run(q: String) {
        val term = q.trim()
        if (term.length < 2) return
        _state.update { it.copy(loading = true, error = null) }
        runCatching { repo.search(term) }
            .onSuccess { resp ->
                historyStore.add(term)
                _state.update {
                    it.copy(
                        results = resp.posts,
                        topics = resp.topics.associateBy { t -> t.id },
                        loading = false,
                        searched = true,
                        history = historyStore.list(),
                        error = resp.grouped?.error,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(loading = false, searched = true, error = e.message ?: "搜索失败")
                }
            }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyStore.clear()
            _state.update { it.copy(history = emptyList()) }
        }
    }

    fun topicTitleFor(topicId: Long): String =
        _state.value.topics[topicId]?.plainTitle ?: ""
}
