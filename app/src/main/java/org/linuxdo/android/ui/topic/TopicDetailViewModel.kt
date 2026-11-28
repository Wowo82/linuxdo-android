package org.linuxdo.android.ui.topic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.data.UserRepository
import org.linuxdo.android.data.model.Post
import org.linuxdo.android.data.model.TopicDetail
import org.linuxdo.android.data.remote.ApiException

/** 单个帖子的本地互动状态（点赞），覆盖服务端返回值 */
data class PostInteraction(val liked: Boolean, val likeCount: Int)

data class TopicState(
    val detail: TopicDetail? = null,
    val posts: List<Post> = emptyList(),
    val interactions: Map<Long, PostInteraction> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val sending: Boolean = false,
    val sendError: String? = null,
    val replyTo: Post? = null,
    val bookmarking: Boolean = false,
    /** 正在按楼层号定位（深链 / 跳楼），期间不要重复触发滚动 */
    val jumping: Boolean = false,
) {
    fun likedOf(post: Post): Boolean =
        interactions[post.id]?.liked ?: post.liked

    fun likeCountOf(post: Post): Int =
        interactions[post.id]?.likeCount ?: post.likeCount
}

/**
 * 话题详情。
 *
 * topic.json 只返回前 20 楼 + stream（全部 post id）。
 * 往下滑到底时按 postNumber 继续拉 /t/{id}.json?post_number=N。
 * 外部链接带楼层号时（`/t/slug/123/7`）首次加载就从第 7 楼开始取。
 */
class TopicDetailViewModel(
    private val repo: UserRepository,
    private val topicId: Long,
    private val initialPostNumber: Int? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TopicState())
    val state: StateFlow<TopicState> = _state.asStateFlow()

    private val loadedPostIds = mutableSetOf<Long>()
    private var loadingMore = false

    init {
        load(initialPostNumber)
    }

    fun load(fromPostNumber: Int? = null) {
        _state.update { it.copy(loading = true, error = null, jumping = fromPostNumber != null) }
        viewModelScope.launch {
            // 带楼层号时直接用 post_number 取那一段，省掉「先拉第 1 页再补」
            val fetch = if (fromPostNumber != null) {
                runCatching { repo.postsNear(topicId, fromPostNumber) }
                    .recoverCatching { repo.topic(topicId) }
            } else {
                runCatching { repo.topic(topicId) }
            }
            fetch
                .onSuccess { detail ->
                    loadedPostIds.clear()
                    detail.postStream.posts.forEach { loadedPostIds.add(it.id) }
                    _state.update {
                        it.copy(
                            detail = detail,
                            posts = detail.postStream.posts.sortedBy { p -> p.postNumber },
                            loading = false,
                            error = null,
                            jumping = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.friendlyMessage(), jumping = false)
                    }
                }
        }
    }

    fun refresh() = load()

    fun loadMore() {
        val s = _state.value
        val detail = s.detail ?: return
        if (loadingMore) return
        if (s.posts.size >= detail.postsCount) return

        val lastNumber = s.posts.maxOfOrNull { it.postNumber } ?: 1
        val next = lastNumber + 1
        if (next > detail.highestPostNumber) return

        loadingMore = true
        viewModelScope.launch {
            runCatching { repo.postsNear(topicId, next) }
                .onSuccess { more ->
                    val fresh = more.postStream.posts.filter { it.id !in loadedPostIds }
                    fresh.forEach { loadedPostIds.add(it.id) }
                    if (fresh.isNotEmpty()) {
                        _state.update {
                            it.copy(
                                posts = (it.posts + fresh)
                                    .distinctBy { p -> p.id }
                                    .sortedBy { p -> p.postNumber },
                            )
                        }
                    }
                }
            loadingMore = false
        }
    }

    fun setReplyTo(post: Post?) = _state.update { it.copy(replyTo = post) }

    fun sendReply(raw: String, onDone: (Boolean) -> Unit) {
        val text = raw.trim()
        if (text.isEmpty()) return
        _state.update { it.copy(sending = true, sendError = null) }
        viewModelScope.launch {
            repo.reply(topicId, text, _state.value.replyTo?.postNumber)
                .onSuccess {
                    _state.update { it.copy(sending = false, replyTo = null) }
                    onDone(true)
                    load()
                }
                .onFailure { e ->
                    _state.update { it.copy(sending = false, sendError = e.friendlyMessage()) }
                    onDone(false)
                }
        }
    }

    fun toggleLike(post: Post) {
        val current = _state.value
        val likedNow = current.likedOf(post)
        val countNow = current.likeCountOf(post)
        // 乐观更新
        _state.update {
            it.copy(
                interactions = it.interactions + (post.id to PostInteraction(
                    liked = !likedNow,
                    likeCount = (countNow + if (likedNow) -1 else 1).coerceAtLeast(0),
                ))
            )
        }
        viewModelScope.launch {
            repo.toggleLike(post.id, likedNow)
                .onSuccess { serverCount ->
                    if (serverCount >= 0) {
                        _state.update {
                            it.copy(
                                interactions = it.interactions + (post.id to PostInteraction(
                                    liked = !likedNow,
                                    likeCount = serverCount,
                                ))
                            )
                        }
                    }
                }
                .onFailure {
                    // 回滚
                    _state.update {
                        it.copy(
                            interactions = it.interactions + (post.id to PostInteraction(
                                liked = likedNow,
                                likeCount = countNow,
                            ))
                        )
                    }
                }
        }
    }

    fun toggleBookmark() {
        val detail = _state.value.detail ?: return
        if (_state.value.bookmarking) return
        _state.update { it.copy(bookmarking = true) }
        viewModelScope.launch {
            repo.toggleBookmark(topicId, detail.bookmarked)
                .onSuccess { now ->
                    _state.update {
                        it.copy(
                            bookmarking = false,
                            detail = it.detail?.copy(bookmarked = now),
                        )
                    }
                }
                .onFailure { _state.update { it.copy(bookmarking = false) } }
        }
    }

    fun markRead(postNumber: Int) {
        viewModelScope.launch { repo.markTopicRead(topicId, postNumber) }
    }
}

internal fun Throwable.friendlyMessage(): String = when (this) {
    is ApiException -> when (kind) {
        ApiException.Kind.NETWORK -> "网络连接失败"
        ApiException.Kind.AUTH -> "需要登录"
        ApiException.Kind.RATE_LIMIT -> "操作太频繁"
        ApiException.Kind.SERVER -> "服务器错误"
        else -> message ?: "未知错误"
    }
    else -> message ?: "未知错误"
}
