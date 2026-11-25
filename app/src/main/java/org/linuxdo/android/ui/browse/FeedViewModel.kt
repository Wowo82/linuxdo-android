package org.linuxdo.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.data.UserRepository
import org.linuxdo.android.data.model.Category
import org.linuxdo.android.data.model.Topic
import org.linuxdo.android.data.model.User

data class FeedState(
    val filter: UserRepository.ListFilter = UserRepository.ListFilter.LATEST,
    val period: UserRepository.Period = UserRepository.Period.WEEKLY,
    val categoryId: Long? = null,
    val topics: List<Topic> = emptyList(),
    val users: Map<Long, User> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val page: Int = 0,
)

/**
 * 话题列表 ViewModel。每个 Tab 持有一个实例，各自保存自己的筛选状态。
 */
class FeedViewModel(private val repo: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(FeedState())
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private var categoriesLoaded = false

    fun ensureLoaded() {
        val s = _state.value
        if (s.topics.isNotEmpty() || s.loading || s.refreshing) return
        load(refresh = false)
    }

    fun setFilter(filter: UserRepository.ListFilter, categoryId: Long? = _state.value.categoryId) {
        val s = _state.value
        if (s.filter == filter && s.categoryId == categoryId) return
        _state.update {
            it.copy(
                filter = filter,
                categoryId = categoryId,
                topics = emptyList(),
                page = 0,
                endReached = false,
                error = null,
            )
        }
        load(refresh = false)
    }

    fun setPeriod(period: UserRepository.Period) {
        _state.update {
            it.copy(period = period, page = 0, topics = emptyList(), endReached = false)
        }
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.refreshing || s.loadingMore || s.endReached) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching {
                repo.topicList(
                    filter = s.filter,
                    page = s.page + 1,
                    categoryId = s.categoryId,
                    period = s.period,
                )
            }.onSuccess { resp ->
                val incoming = resp.topicList.topics
                val merged = (s.topics + incoming).distinctBy { it.id }
                _state.update {
                    it.copy(
                        topics = merged,
                        users = it.users + resp.users.associateBy { u -> u.id },
                        page = s.page + 1,
                        loadingMore = false,
                        endReached = incoming.isEmpty() || resp.topicList.moreTopicsUrl == null,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(loadingMore = false, error = e.friendly()) }
            }
        }
    }

    fun load(refresh: Boolean) {
        val s = _state.value
        if (!refresh && (s.loading || s.refreshing)) return
        _state.update {
            it.copy(
                loading = it.topics.isEmpty() && !refresh,
                refreshing = refresh,
                error = null,
            )
        }
        viewModelScope.launch {
            if (!categoriesLoaded) {
                runCatching { repo.categories() }.onSuccess { cl ->
                    categoriesLoaded = true
                    _state.update { it.copy(categories = cl.categories) }
                }
            }
            runCatching {
                repo.topicList(
                    filter = s.filter,
                    page = 0,
                    categoryId = s.categoryId,
                    period = s.period,
                    forceReload = refresh,
                )
            }.onSuccess { resp ->
                _state.update {
                    it.copy(
                        topics = resp.topicList.topics,
                        users = resp.users.associateBy { u -> u.id },
                        loading = false,
                        refreshing = false,
                        page = 0,
                        endReached = resp.topicList.moreTopicsUrl == null,
                        error = null,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(loading = false, refreshing = false, error = e.friendly())
                }
            }
        }
    }

    fun categoryFor(topic: Topic): Category? =
        topic.categoryId?.let { id -> _state.value.categories.firstOrNull { it.id == id } }

    fun userFor(id: Long): User? = _state.value.users[id]
}

internal fun Throwable.friendly(): String = when (this) {
    is org.linuxdo.android.data.remote.ApiException -> when (kind) {
        org.linuxdo.android.data.remote.ApiException.Kind.NETWORK -> "网络连接失败，检查网络或试试关闭 DoH"
        org.linuxdo.android.data.remote.ApiException.Kind.AUTH -> "需要登录后才能查看"
        org.linuxdo.android.data.remote.ApiException.Kind.RATE_LIMIT -> "请求太频繁，稍后再试"
        org.linuxdo.android.data.remote.ApiException.Kind.SERVER -> "服务器出错了"
        else -> message ?: "未知错误"
    }
    else -> message ?: "未知错误"
}
