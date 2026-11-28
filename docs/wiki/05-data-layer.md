# 05 · 数据层参考

数据层负责三件事：**发请求并解析**（`UserRepository`）、**维持登录态与站点配置**（`SessionStore`）、
**本地持久化**（`DraftStore` / `SearchHistoryStore`）。它不持有任何 Compose 状态。

```text
UI / ViewModel
     │ suspend 方法 + Result
┌────▼─────────────────────────────────────────────┐
│ UserRepository                                   │
│  ├─ 业务方法（会话 / 列表 / 详情 / 写 / 通知…）     │
│  ├─ 列表 LRU 缓存                                 │
│  └─ 登录态写回 SessionStore                       │
└────┬──────────────────────────────┬──────────────┘
     │ DiscourseApi                 │ SessionStore
┌────▼──────────────┐        ┌──────▼──────────────────────────┐
│ data/remote        │        │ SharedPreferences（普通）         │
│  HTTPS + CSRF      │        │ EncryptedSharedPreferences（加密）│
└────────────────────┘        └─────────────────────────────────┘
```

---

## 1. AppContainer

文件：[AppContainer.kt](../../app/src/main/java/org/linuxdo/android/AppContainer.kt)

构造时即创建全部单例。没有延迟初始化（除了 `SessionStore` 内部的 `lazy` 分区）。

| 成员 | 类型 | 说明 |
|---|---|---|
| `sessionStore` | `SessionStore` | 站点地址、用户信息、加密 cookie / CSRF |
| `imageHttpClient` | `OkHttpClient` | 图片专用：连接 15s、读 30s、跟随重定向、**不挂 cookie jar** |
| `api` | `DiscourseApi` | 四个 provider 动态读取站点与会话，保证切站点立即生效 |
| `repository` | `UserRepository` | 业务入口 |
| `draftStore` | `DraftStore` | DataStore 草稿 / 阅读位置（当前未被 UI 使用） |
| `searchHistoryStore` | `SearchHistoryStore` | DataStore 搜索历史 |

装配代码：

```kotlin
val api = DiscourseApi(
    baseUrlProvider = { sessionStore.baseUrl },
    clientProvider = {
        HttpClients.forSite(
            baseUrl = sessionStore.baseUrl,
            cookieJar = sessionStore.cookieJar,
            csrfTokens = sessionStore.csrfTokens,
        )
    },
    csrfProvider = { sessionStore.csrfTokens.current() },
    csrfPersist = { token -> sessionStore.csrfTokens.update(token) },
)
```

`onSiteChanged()`：调用 `HttpClients.invalidate()`，丢弃按站点缓存的 OkHttpClient。
切换站点后应调用；当前只有未被 UI 使用的 `MainViewModel.changeServer()` 调用了它。

---

## 2. SessionStore

文件：[SessionStore.kt](../../app/src/main/java/org/linuxdo/android/data/SessionStore.kt)

### 2.1 存储分区

| 存储 | 文件名 | 加密 | 内容 |
|---|---|---|---|
| `plain: SharedPreferences` | `linuxdo.prefs` | 否 | `base_url`、`username`、`user_id`、`site_title`、`site_icon` |
| `secure: EncryptedSharedPreferences` | `linuxdo.secrets` | 是 | `cookies_v1`、`csrf_v1` |

加密实现：

- `MasterKey`：`AES256_GCM`，由 Android Keystore 托管；
- `EncryptedSharedPreferences`：key 用 `AES256_SIV`，value 用 `AES256_GCM`；
- `secure` 是 `lazy`，首次访问 cookie jar / CSRF 时才创建。

### 2.2 属性

| 属性 | 类型 | 读 / 写行为 |
|---|---|---|
| `baseUrl` | `String` | 读：`base_url` → `trimEnd('/')` → 空则回落 `BuildConfig.DEFAULT_BASE_URL`；写：`trimEnd('/')` 后存入普通 prefs |
| `username` | `String?` | 普通 prefs；登录成功后写入 |
| `userId` | `Long` | 普通 prefs；默认 `-1L` |
| `siteTitle` | `String?` | 启动时由 `siteBasicInfo()` 缓存 |
| `siteIconUrl` | `String?` | 同上，取 `logo_small_url` → `apple_touch_icon_url` → `favicon_url` |
| `isLoggedIn` | `Boolean` | `username != null`，不做网络校验 |
| `cookieJar` | `PersistentCookieJar` | `lazy`；load/save 都落到 `cookies_v1` |
| `csrfTokens` | `CsrfTokens` | `lazy`；load/save 落到 `csrf_v1` |

### 2.3 登出清理

```kotlin
fun clearCookies() {
    cookieJar.clear()
    csrfTokens.clear()
    secure.edit().remove(KEY_COOKIES).remove(KEY_CSRF).apply()
    plain.edit().remove(KEY_USERNAME).remove(KEY_USER_ID).apply()
}
```

**不会清理**：`base_url`、`site_title`、`site_icon`。因此切换站点后旧站点标题会短暂保留，
直到下一次 `boot()` 更新（见 02 §10）。

### 2.4 Cookie 序列化格式

`okhttp3.Cookie` 没有 serializer，因此使用自定义文本格式：

```text
name \u0001 value \u0001 domain \u0001 path \u0001 expiresAt \u0001 secure \u0001 httpOnly
```

- 每条 cookie 一行（`\n` 分隔）；
- 字段固定 7 个，顺序固定；`secure` / `httpOnly` 存 `"true"` / `"false"`；
- `decodeCookies()`：行数不足 7 个字段时跳过；单条构造失败用 `runCatching` 丢弃，不影响其他 cookie。

选择自定义格式而不是 JSON：字段固定、代码量小、不引入模型层依赖。

---

## 3. UserRepository

文件：[UserRepository.kt](../../app/src/main/java/org/linuxdo/android/data/UserRepository.kt)

### 3.1 结构

| 成员 | 类型 | 作用 |
|---|---|---|
| `api` | `DiscourseApi` | 所有 HTTP |
| `sessionStore` | `SessionStore` | 读 / 写登录态与站点信息 |
| `json` | `Json` | 复用 `api.json` 的配置 |
| `topicCache` | `LinkedHashMap<String, TopicListResponse>` | 列表页 LRU 缓存 |
| `cacheLock` | `Any` | 保护缓存读写 |

`json` 配置来自 `DiscourseApi`：

```kotlin
Json {
    ignoreUnknownKeys = true   // 插件增删字段不崩
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = false
}
```

### 3.2 列表缓存

```kotlin
private val topicCache = object : LinkedHashMap<String, TopicListResponse>(32, 0.75f, true) {
    override fun removeEldestEntry(...) = size > MAX_CACHED_LISTS   // 30
}
```

| 项 | 规则 |
|---|---|
| key | `${filter.path}\|${categoryId ?: -1}\|${period?.value ?: "-"}\|$page` |
| 命中 | `topicList(forceReload = false)` 直接返回缓存 |
| 写入 | 每次网络成功都覆盖同 key |
| 淘汰 | 超过 30 组时按访问顺序淘汰最久未用的 |
| 失效 | `invalidateLists()`：登出、切站点、手动调用 |
| 并发 | 读写都在 `synchronized(cacheLock)` 中 |

示例 key：`latest|-1|-|0`、`top|-1|weekly|1`、`latest|67|-|0`。

### 3.3 枚举

```kotlin
enum class ListFilter(val path: String) {
    LATEST("latest"), TOP("top"), NEW("new"), UNREAD("unread"),
    HOT("hot"), CATEGORIES("categories"), BOOKMARKS("bookmarks");
    companion object { fun from(name: String?): ListFilter }  // 忽略大小写，未知 → LATEST
}

enum class Period(val value: String) {
    DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly"),
    QUARTERLY("quarterly"), YEARLY("yearly"), ALL("all"),
}
```

`BOOKMARKS` 与 `CATEGORIES` 已定义，但当前 UI 只使用前 5 个 filter。

### 3.4 方法：会话 / 站点

| 方法 | 端点 | 返回 | 说明 |
|---|---|---|---|
| `refreshCsrfToken(): String?` | `GET /session/csrf.json` | token 或 null | 调用 `api.refreshCsrf()` 后写入 `sessionStore.csrfTokens` |
| `refreshSession(): SessionResponse` | `GET /session/current.json` | 会话 | 写回 `username` / `userId`；未登录会抛 `ApiException(AUTH)` |
| `currentUserOrNull(): User?` | 同上 | `User?` | 捕获所有 `ApiException` → null |
| `siteBasicInfo(): SiteBasicInfo` | `GET /site/basic-info.json` | 站点信息 | 缓存 `siteTitle` / `siteIconUrl` |
| `login(login, password): Result<User>` | `POST /session` → `GET /session/current.json` | `Result<User>` | 见下方细节 |
| `logout()` | `DELETE /session/{username}` | Unit | 删除失败也继续清本地 |

`login()` 细节：

```kotlin
api.postForm("/session", mapOf(
    "login" to login.trim(),
    "password" to password,
    "second_factor_method" to "1",
))
// LoginResponse.success == (error == null)
// !success → throw IllegalStateException(error ?: reason ?: "登录失败")
// GET /session/current.json → currentUser 为空则抛异常
// 写 username / userId → refreshCsrfToken() → 返回 User
```

### 3.5 方法：话题列表

| 方法 | 端点 / 规则 | 说明 |
|---|---|---|
| `listCacheKey(filter, page, categoryId, period)` | 纯函数 | 生成缓存 key |
| `topicList(filter, page=0, categoryId=null, period=null, forceReload=false)` | `/{filter}.json` 或 `/c/{categoryId}/{filter}.json` | 参数：`page`、`per_page=30`；TOP 加 `period`；有分类加 `category_id` |
| `categories(): CategoryList` | `GET /categories.json` | 捕获 `ApiException` 时返回空列表；其他异常仍会抛出 |
| `invalidateLists()` | — | 清空 LRU |

> **结构提示**：Discourse 官方文档与 `docs/API.md` 中 `GET /categories.json` 的响应外层是
> `{"category_list": {...}}`，而 `CategoryList` 的字段定义在根层。`categories()` 目前直接把整个响应
> 解析为 `CategoryList`，在标准响应下外层会被 `ignoreUnknownKeys` 忽略，得到空分类列表。
> 若在真实站点上发现分类标签为空、发帖页无法选择分类，应优先检查这里；
> 修复可新增 wrapper 模型，或复用 `TopicList.categoryList` 的解码路径。本文只记录现状，未改动代码。

路径拼接规则：

```text
categoryId != null && filter != CATEGORIES
    → /c/{categoryId}/{filter.path}.json
否则
    → /{filter.path}.json
```

即使分类维度使用 `/c/…`，仍会额外带 `category_id` 查询参数（Discourse 接受）。

### 3.6 方法：话题详情

| 方法 | 端点 | 说明 |
|---|---|---|
| `topic(topicId): TopicDetail` | `GET /t/{id}.json?include_raw=true` | 首次加载；`include_raw` 便于后续编辑 |
| `postsNear(topicId, postNumber): TopicDetail` | `GET /t/{id}.json?post_number=N` | 楼层分页；返回目标楼前后若干楼，需去重 |
| `markTopicRead(topicId, postNumber)` | `POST /t/{id}/timings` | form：`timings[0][post_number]`、`timings[0][msecs]=1000`；失败静默；**当前无 UI 调用** |

### 3.7 方法：写操作

| 方法 | 端点 | 返回 | 状态 |
|---|---|---|---|
| `createTopic(title, raw, categoryId): Result<Long>` | `POST /posts`（title / raw / category） | 新 `topic_id` | 已接入 `ComposerScreen` |
| `reply(topicId, raw, replyToPostNumber=null): Result<Long>` | `POST /posts`（topic_id / raw / reply_to_post_number?） | 新 `post.id` | 已接入详情页 |
| `editPost(postId, raw): Result<Unit>` | `PUT /posts/{id}`（post[raw]） | Unit | **未接入 UI** |
| `toggleLike(postId, currentlyLiked): Result<Int>` | `DELETE /post_actions/{id}?post_action_type_id=2` / `POST /post_actions` | 最新点赞数，取不到为 `-1` | 已接入详情页 |
| `toggleBookmark(topicId, currentlyBookmarked): Result<Boolean>` | `DELETE /bookmarks/{id}` / `POST /bookmarks` | 目标状态 | 已接入详情页 |

`toggleLike()` 的 count 提取：

```kotlin
Regex("\"id\"\\s*:\\s*2\\b[^}]*?\"count\"\\s*:\\s*(\\d+)")
    .find(resp)?.groupValues?.get(1)?.toInt() ?: -1
```

这是对 `actions_summary` 里 `id == 2` 的轻量匹配，未做完整 JSON 解析；
响应格式变化时会返回 `-1`，调用方保留本地推算的数值。

### 3.8 方法：通知 / 搜索 / 用户

| 方法 | 端点 | 说明 |
|---|---|---|
| `notifications(offset = 0): NotificationsResponse` | `GET /notifications.json?offset=` | 只拉一页，UI 未分页 |
| `markAllNotificationsRead()` | `PUT /notifications/mark-read`（dismiss=true） | 失败静默；**未调用** |
| `search(term, page = 1): SearchResponse` | `GET /search.json?q=&page=&include_blurbs=true` | 关键词 trim |
| `user(username): UserDetailResponse` | `GET /u/{username}.json` | username URL encode |
| `userTopics(username, page = 0): TopicListResponse` | `GET /topics/created-by/{username}.json?page=` | **未接入 UI** |

### 3.9 使用状态总览

| 方法 | UI 使用方 |
|---|---|
| `refreshCsrfToken` | `MainViewModel.boot()`、`login()` 内部 |
| `refreshSession` | `MainViewModel.boot()` / `refreshUnread()` |
| `currentUserOrNull` | `MainViewModel.boot()` |
| `siteBasicInfo` | `MainViewModel.boot()` / `changeServer()` |
| `login` / `logout` | `ProfileScreen` / `MainViewModel` |
| `topicList` / `categories` | `FeedViewModel`、`ComposerScreen` |
| `topic` / `postsNear` | `TopicDetailViewModel` |
| `createTopic` | `ComposerScreen` |
| `reply` | `TopicDetailViewModel.sendReply` |
| `toggleLike` / `toggleBookmark` | `TopicDetailViewModel` |
| `notifications` | `NotificationsScreen` |
| `search` | `SearchViewModel` |
| `markTopicRead` | 定义了 VM 方法 `markRead()`，但无 UI 调用 |
| `editPost` / `userTopics` / `markAllNotificationsRead` | 无调用方 |

---

## 4. DraftStore

文件：[DraftStore.kt](../../app/src/main/java/org/linuxdo/android/data/local/DraftStore.kt)

DataStore 名称：`linuxdo.drafts`（`preferencesDataStore` 扩展属性）。

| 方法 | 行为 |
|---|---|
| `save(key, text)` | 写入 `"$text\u0001${System.currentTimeMillis()}"` |
| `load(key): Entry?` | 取最后一个 `\u0001` 分割文字与时间戳；无分隔符时 `updatedAt=0` |
| `delete(key)` | 删除该 key |
| `allKeys(): List<String>` | 当前所有 key 名 |
| `saveLastRead(topicId, postNumber)` | key = `read_{topicId}`，value = 楼层号 |
| `lastRead(topicId): Int?` | 读回楼层号 |
| `cacheSiteBlob(blob)` / `siteBlob()` | key = `site_blob`，缓存站点配置 JSON |

`Entry`：

```kotlin
data class Entry(val text: String, val updatedAt: Long)
```

**约定**：草稿 key 采用 `topic_123`（某话题回复）、`new_topic_5`（发新帖 + 分类 id）。
README 说明服务端 `/drafts.json` 才是权威，本地只是缓存。

**现状**：`AppContainer` 创建了实例，但没有任何 Screen 使用。接入时建议：

1. `ComposerScreen`：`LaunchedEffect` 读取 `load("new_topic_$categoryId")`，输入变化时防抖保存，发布成功后 `delete`；
2. `TopicDetailScreen`：进入时 `lastRead(topicId)`，滚动停止时 `saveLastRead`。

---

## 5. SearchHistoryStore

文件：[SearchHistoryStore.kt](../../app/src/main/java/org/linuxdo/android/data/local/SearchHistoryStore.kt)

DataStore 名称：`linuxdo.search`，key：`history`（`stringSetPreferencesKey`）。

| 方法 | 行为 |
|---|---|
| `add(term)` | trim；长度 < 2 直接忽略；`(新词 + 旧集合去重).take(20).toSet()` |
| `list()` | 返回 `Set.toList()` |
| `clear()` | 删除 `history` |

**注意**：使用 `StringSet` 存储，`Set` 不保留插入顺序，因此「新的在前」的注释与
`list()` 的实际顺序并不严格一致。若需要稳定排序，应改为存 JSON 数组字符串
（DataStore Preferences 不支持有序集合）。

`SearchViewModel` 在每次成功搜索后 `add(term)`，并立即回读 `historyStore.list()` 更新 UI。
