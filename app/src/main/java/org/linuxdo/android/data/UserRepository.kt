package org.linuxdo.android.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.linuxdo.android.data.model.CategoriesResponse
import org.linuxdo.android.data.model.CategoryList
import org.linuxdo.android.data.model.LoginResponse
import org.linuxdo.android.data.model.NotificationsResponse
import org.linuxdo.android.data.model.SearchResponse
import org.linuxdo.android.data.model.SessionResponse
import org.linuxdo.android.data.model.SiteBasicInfo
import org.linuxdo.android.data.model.TopicDetail
import org.linuxdo.android.data.model.TopicListResponse
import org.linuxdo.android.data.model.User
import org.linuxdo.android.data.model.UserDetailResponse
import org.linuxdo.android.data.remote.ApiException
import org.linuxdo.android.data.remote.DiscourseApi
import java.net.URLEncoder

/**
 * 业务仓库：把 DiscourseApi 的 JSON 变成 UI 能用的模型，并负责登录态维护。
 */
class UserRepository(
    private val api: DiscourseApi,
    private val sessionStore: SessionStore,
) {
    private val json = api.json

    /**
     * 列表页内存缓存，key 见 listCacheKey()。
     * 用 LRU 封顶：无限滚动 + 多 filter/分类/周期组合会让 key 无上限增长，
     * 长时间刷列表内存只涨不降。
     */
    private val topicCache = object : LinkedHashMap<String, TopicListResponse>(
        /* initialCapacity = */ 32,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TopicListResponse>?): Boolean =
            size > MAX_CACHED_LISTS
    }

    /** 缓存条数上限见 MAX_CACHED_LISTS；LRU 的读写都要进这把锁 */
    private val cacheLock = Any()

    /**
     * 刷新 CSRF token：登录后、切站点后、老版本升级上来的首次启动都会用到。
     * 实现在 [DiscourseApi.refreshCsrf]（GET，不会触发写操作的 403 重试分支）。
     */
    suspend fun refreshCsrfToken(): String? {
        val token = api.refreshCsrf() ?: return null
        sessionStore.csrfTokens.update(token)
        return token
    }

    /** 分类树变化很少，重复进版块/发帖页时避免反复拉整棵分类树 */
    @Volatile
    private var categoryCache: CategoryList? = null

    /** 分类缓存的锁：读写在 IO 线程，invalidateLists 可能在主线程调用 */
    private val categoryLock = Any()

    // ============================================================
    // 会话 / 站点
    // ============================================================

    suspend fun refreshSession(): SessionResponse {
        val s: SessionResponse = json.decodeFromString(api.getRaw("/session/current.json"))
        sessionStore.username = s.currentUser?.username
        sessionStore.userId = s.currentUser?.id ?: -1L
        return s
    }

    /** 未登录时 /session/current.json 返回 403，这里统一成 null */
    suspend fun currentUserOrNull(): User? = try {
        refreshSession().currentUser
    } catch (e: ApiException) {
        null
    }

    suspend fun siteBasicInfo(): SiteBasicInfo {
        val info: SiteBasicInfo = json.decodeFromString(api.getRaw("/site/basic-info.json"))
        sessionStore.siteTitle = info.title
        sessionStore.siteIconUrl = info.logoSmallUrl ?: info.appleTouchIconUrl ?: info.faviconUrl
        return info
    }

    suspend fun login(login: String, password: String): Result<User> = runCatching {
        val raw = api.postForm(
            "/session",
            mapOf(
                "login" to login.trim(),
                "password" to password,
                "second_factor_method" to "1",
            ),
        )
        val parsed: LoginResponse = json.decodeFromString(raw)
        if (!parsed.success) {
            throw IllegalStateException(parsed.error ?: parsed.reason ?: "登录失败")
        }
        val session: SessionResponse = json.decodeFromString(api.getRaw("/session/current.json"))
        val u = session.currentUser ?: throw IllegalStateException("登录成功但拿不到用户信息")
        sessionStore.username = u.username
        sessionStore.userId = u.id
        // 登录后立刻取 CSRF token：不然第一次发帖 / 点赞会先吃一个 403 再重试
        refreshCsrfToken()
        u
    }

    suspend fun logout() {
        sessionStore.username?.let { runCatching { api.deleteForm("/session/$it") } }
        sessionStore.clearCookies()
        invalidateLists()
    }

    // ============================================================
    // 话题列表
    // ============================================================

    enum class ListFilter(val path: String) {
        LATEST("latest"), TOP("top"), NEW("new"), UNREAD("unread"),
        HOT("hot"), CATEGORIES("categories"), BOOKMARKS("bookmarks");

        companion object {
            fun from(name: String?): ListFilter =
                entries.firstOrNull { it.name.equals(name, true) } ?: LATEST
        }
    }

    enum class Period(val value: String) {
        DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly"),
        QUARTERLY("quarterly"), YEARLY("yearly"), ALL("all"),
    }

    fun listCacheKey(
        filter: ListFilter,
        page: Int,
        categoryId: Long?,
        period: Period?,
        categorySlug: String? = null,
    ): String = buildString {
        append(filter.path).append('|')
        append(categoryId ?: -1).append('|')
        append(period?.value ?: "-").append('|')
        append(page)
        // slug 进 key：分类改名后 slug 会变，不能和旧 slug 的缓存混用
        if (!categorySlug.isNullOrBlank()) append('|').append(categorySlug)
    }

    suspend fun topicList(
        filter: ListFilter,
        page: Int = 0,
        categoryId: Long? = null,
        categorySlug: String? = null,
        period: Period? = null,
        forceReload: Boolean = false,
    ): TopicListResponse {
        val key = listCacheKey(filter, page, categoryId, period, categorySlug)
        if (!forceReload) cachedList(key)?.let { return it }

        // 分类列表的规范路由是 /c/{slug}/{id}/l/{filter}.json（见 CategoryRoute）
        val path = CategoryRoute.path(filter.path, categoryId, categorySlug)
        val params = buildMap<String, String?> {
            put("page", page.toString())
            put("per_page", "30")
            if (filter == ListFilter.TOP && period != null) put("period", period.value)
        }
        val resp: TopicListResponse = json.decodeFromString(api.getRaw(path, params))
        synchronized(cacheLock) { topicCache[key] = resp }
        return resp
    }

    private fun cachedList(key: String): TopicListResponse? =
        synchronized(cacheLock) { topicCache[key] }

    fun invalidateLists() {
        synchronized(cacheLock) { topicCache.clear() }
        synchronized(categoryLock) { categoryCache = null }
    }

    suspend fun categories(forceReload: Boolean = false): CategoryList {
        if (!forceReload) synchronized(categoryLock) { categoryCache }?.let { return it }
        return try {
            val raw = api.getRaw("/categories.json", mapOf("include_subcategories" to "true"))
            val list = json.decodeFromString<CategoriesResponse>(raw).categoryList
            synchronized(categoryLock) { categoryCache = list }
            list
        } catch (e: ApiException) {
            synchronized(categoryLock) { categoryCache } ?: CategoryList()
        }
    }

    // ============================================================
    // 话题详情
    // ============================================================

    suspend fun topic(topicId: Long): TopicDetail =
        json.decodeFromString(api.getRaw("/t/$topicId.json", mapOf("include_raw" to "true")))

    /** 加载指定楼层附近的帖子，用于无限滚动 */
    suspend fun postsNear(topicId: Long, postNumber: Int): TopicDetail =
        json.decodeFromString(
            api.getRaw("/t/$topicId.json", mapOf("post_number" to postNumber.toString()))
        )

    suspend fun markTopicRead(topicId: Long, postNumber: Int) {
        runCatching {
            api.postForm(
                "/t/$topicId/timings",
                mapOf(
                    "timings[0][post_number]" to postNumber.toString(),
                    "timings[0][msecs]" to "1000",
                ),
            )
        }
    }

    // ============================================================
    // 发帖 / 回复 / 互动
    // ============================================================

    suspend fun createTopic(
        title: String,
        raw: String,
        categoryId: Long,
    ): Result<Long> = runCatching {
        val resp = api.postForm(
            "/posts",
            mapOf(
                "title" to title,
                "raw" to raw,
                "category" to categoryId.toString(),
            ),
        )
        val obj = json.parseToJsonElement(resp) as JsonObject
        obj["topic_id"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalStateException("创建成功但没返回 topic_id")
    }

    suspend fun reply(
        topicId: Long,
        raw: String,
        replyToPostNumber: Int? = null,
    ): Result<Long> = runCatching {
        val form = buildMap {
            put("topic_id", topicId.toString())
            put("raw", raw)
            if (replyToPostNumber != null) {
                put("reply_to_post_number", replyToPostNumber.toString())
            }
        }
        val resp = api.postForm("/posts", form)
        val obj = json.parseToJsonElement(resp) as JsonObject
        obj["id"]?.jsonPrimitive?.longOrNull
            ?: throw IllegalStateException("发送成功但没返回 post id")
    }

    suspend fun editPost(postId: Long, raw: String): Result<Unit> = runCatching {
        api.putForm("/posts/$postId", mapOf("post[raw]" to raw))
        Unit
    }

    /** 点赞 / 取消点赞，返回最新点赞数（拿不到时返回 -1） */
    suspend fun toggleLike(postId: Long, currentlyLiked: Boolean): Result<Int> = runCatching {
        val resp = if (currentlyLiked) {
            api.deleteForm("/post_actions/$postId", mapOf("post_action_type_id" to "2"))
        } else {
            api.postForm(
                "/post_actions",
                mapOf("id" to postId.toString(), "post_action_type_id" to "2"),
            )
        }
        Regex("\"id\"\\s*:\\s*2\\b[^}]*?\"count\"\\s*:\\s*(\\d+)")
            .find(resp)?.groupValues?.get(1)?.toInt() ?: -1
    }

    suspend fun toggleBookmark(topicId: Long, currentlyBookmarked: Boolean): Result<Boolean> =
        runCatching {
            if (currentlyBookmarked) {
                api.deleteForm("/bookmarks/$topicId")
            } else {
                api.postForm(
                    "/bookmarks",
                    mapOf("bookmarkable_id" to topicId.toString(), "bookmarkable_type" to "Topic"),
                )
            }
            !currentlyBookmarked
        }

    // ============================================================
    // 通知
    // ============================================================

    suspend fun notifications(offset: Int = 0): NotificationsResponse =
        json.decodeFromString(
            api.getRaw("/notifications.json", mapOf("offset" to offset.toString()))
        )

    suspend fun markAllNotificationsRead() {
        runCatching { api.putForm("/notifications/mark-read", mapOf("dismiss" to "true")) }
    }


    // ============================================================
    // 搜索
    // ============================================================

    suspend fun search(term: String, page: Int = 1): SearchResponse =
        json.decodeFromString(
            api.getRaw(
                "/search.json",
                mapOf(
                    "q" to term.trim(),
                    "page" to page.toString(),
                    "include_blurbs" to "true",
                ),
            )
        )

    // ============================================================
    // 用户
    // ============================================================

    suspend fun user(username: String): UserDetailResponse =
        json.decodeFromString(api.getRaw("/u/${encode(username)}.json"))

    suspend fun userTopics(username: String, page: Int = 0): TopicListResponse =
        json.decodeFromString(
            api.getRaw(
                "/topics/created-by/${encode(username)}.json",
                mapOf("page" to page.toString()),
            )
        )

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        /** 列表缓存上限 */
        const val MAX_CACHED_LISTS = 30
    }
}
