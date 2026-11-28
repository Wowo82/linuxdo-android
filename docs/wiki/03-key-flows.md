# 03 · 关键流程

本文按「用户触发 → 代码调用链 → 状态变化」拆解主要业务流程。所有类名均可在
[04-module-reference.md](04-module-reference.md) 或各层参考页中找到文件位置。

---

## 1. 启动与根状态初始化

**触发**：点击桌面图标 / 系统拉起 `MainActivity`。

```text
LinuxDoApp.onCreate()
  └─ container = AppContainer(this)
       ├─ SessionStore(context)                 // 读 base_url 等普通 prefs
       ├─ imageHttpClient                       // Coil 专用
       ├─ DiscourseApi(provider 化配置)          // 不在构造时发请求
       ├─ UserRepository(api, sessionStore)
       └─ DraftStore / SearchHistoryStore

MainActivity.onCreate()
  ├─ enableEdgeToEdge()
  ├─ consumeDeepLink(intent)                    // ACTION_VIEW 才解析
  └─ setContent {
       LinuxDoTheme {
         val vm = viewModel(factory = MainViewModelFactory(container))
         val state by vm.state.collectAsState()
         CompositionLocalProvider(LocalSiteBaseUrl provides state.baseUrl) {
           MainScreen(vm, state, container, deepLinkTopicId, deepLinkPostNumber, ...)
         }
       }
     }

MainViewModel.init → boot()
  ├─ repo.siteBasicInfo()                        GET /site/basic-info.json
  │    └─ 写 SessionStore.siteTitle / siteIconUrl
  ├─ repo.currentUserOrNull()                    GET /session/current.json（403 → null）
  ├─ 已登录且 csrfTokens.current() == null → repo.refreshCsrfToken()
  ├─ repo.refreshSession()                       取 all_unread_notifications_count
  └─ MainState(booting=false, baseUrl, siteTitle, siteIcon, user, unread, bootError)
```

**关键点**：

- `bootError` 只在 `siteBasicInfo()` 失败时设置（「无法连接站点，检查网络或服务器地址」）；
- `baseUrl` 取自 `SessionStore`，并在每次 `boot()` 时重新读取，用于 `LocalSiteBaseUrl`；
- 未登录时 `/session/current.json` 返回 403，`currentUserOrNull()` 吞掉 `ApiException`。

---

## 2. 登录

**触发**：「我的」页未登录状态下的登录表单。

```text
ProfileScreen.LoginCard
  └─ 校验非空 → loading = true
      └─ container.repository.login(login, password)
           ├─ POST /session
           │    form: login, password, second_factor_method=1
           ├─ 解析 LoginResponse
           │    └─ success == false → 抛 IllegalStateException(error / reason)
           ├─ GET /session/current.json → SessionResponse
           │    ├─ 写 SessionStore.username / userId
           │    └─ currentUser 为空 → 抛「登录成功但拿不到用户信息」
           ├─ refreshCsrfToken()              GET /session/csrf.json，token 落加密 prefs
           └─ 返回 Result<User>
      ├─ 成功 → onLoggedIn(user)
      │    └─ MainViewModel.setUser(user)
      │         + FeedViewModel.refresh()
      └─ 失败 → error = message
```

**注意**：

- 2FA 账号会返回 `invalid_second_factor`，当前未实现 `POST /session/2fa`；
- 登录密码不落盘；落盘的是服务端下发的 cookie 与 CSRF token（均加密）；
- `refreshCsrfToken()` 在登录后立刻调用，避免第一次写操作先吃 403 再重试。

---

## 3. 登出

```text
ProfileScreen「退出登录」
  └─ MainViewModel.logout()
       └─ UserRepository.logout()
            ├─ DELETE /session/{username}        // runCatching，失败也继续
            ├─ sessionStore.clearCookies()
            │    ├─ cookieJar.clear()            // 内存 + 加密 prefs
            │    ├─ csrfTokens.clear()
            │    └─ 删除 username / userId
            └─ invalidateLists()                 // 清空列表缓存
       └─ MainState(user = null, unreadNotifications = 0)
```

---

## 4. 话题列表：加载 / 筛选 / 刷新 / 分页

### 4.1 首次加载

```text
FeedScreen 组合
  ├─ LaunchedEffect(Unit) → vm.ensureLoaded()
  │    └─ topics 为空 && 未 loading/refreshing → load(refresh = false)
  ├─ load() 内部先加载分类（仅一次）
  │    └─ repo.categories() → GET /categories.json → FeedState.categories
  └─ repo.topicList(filter=LATEST, page=0, categoryId=null, period=WEEKLY)
       ├─ 命中 listCacheKey → 直接返回
       └─ 未命中 → GET /latest.json?page=0&per_page=30
```

### 4.2 切换筛选 / 周期

| 操作 | ViewModel 方法 | 行为 |
|---|---|---|
| 点击「最新 / 热门 / 新帖 / 未读 / 精华」 | `setFilter(filter)` | 相同 filter + category 直接 return；否则清空 topics、page=0、endReached=false 后重新 `load()` |
| 精华下选择日 / 周 / 月 / 季 / 年 / 全部 | `setPeriod(period)` | 清空列表、page=0 后重新 `load()` |
| 下拉刷新 | `refresh()` | `load(refresh = true)` → `forceReload = true` 绕过缓存 |
| 滑到底部 | `loadMore()` | `page + 1` 加载下一页并去重合并 |

分页终止条件：`incoming.isEmpty()` **或** `resp.topicList.moreTopicsUrl == null` 时
`endReached = true`，列表底部显示「已经到底啦」。

### 4.3 缓存行为

- key：`"${filter.path}|${categoryId ?: -1}|${period?.value ?: "-"}|$page"`；
- 上限：30 组（LRU，`accessOrder = true`）；
- 失效：`invalidateLists()`（登出 / 切站点）、`forceReload = true`（下拉刷新）；
- 读取与写入都在 `synchronized(cacheLock)` 下进行。

---

## 5. 话题详情与楼层分页

```text
打开话题（列表点击 / 搜索结果 / 通知 / 深链）
  → MainScreen.openTopicId = id
  → TopicDetailScreen(topicId, initialPostNumber)
       └─ viewModel(key = "topic_{id}_{post ?: 0}")
            └─ TopicDetailViewModel.init → load(initialPostNumber)

说明：只有深链会设置 initialPostNumber，其余入口为 null。

load(fromPostNumber)
  ├─ fromPostNumber != null → repo.postsNear(id, n)
  │                             GET /t/{id}.json?post_number=n
  │                             失败 → 回退 repo.topic(id)
  ├─ else → repo.topic(id)       GET /t/{id}.json?include_raw=true
  ├─ loadedPostIds.clear() 后记录本次所有 post.id
  └─ TopicState(detail, posts = 按 postNumber 升序, loading = false)
```

**继续加载**：

```text
LazyColumn 可见项 >= posts.size - 2
  → vm.loadMore()
       ├─ 防护：loadingMore / posts.size >= detail.postsCount / next > highestPostNumber
       ├─ next = 当前最大 postNumber + 1
       ├─ repo.postsNear(id, next)
       ├─ 过滤已存在 post.id（Discourse 会返回目标楼层前后若干楼）
       └─ 合并、去重、按 postNumber 排序
```

**楼层定位**（深链）：

```text
LaunchedEffect(state.posts, state.jumping)
  ├─ initialPostNumber == null → 不处理
  ├─ 已跳过一次 或 数据未就绪 或 仍在 jumping → 不处理
  ├─ index = posts.indexOfFirst { it.postNumber >= target }
  └─ listState.scrollToItem(index + 1)   // +1 是因为顶部有 header item
```

`jumping` 为 true 时不会重复触发滚动；`jumped` 局部状态保证只跳一次。

---

## 6. 深链打开指定楼层

Manifest 声明：

```xml
<data android:scheme="https" android:host="linux.do" android:pathPrefix="/t/" />
```

链路：

```text
浏览器点击 https://linux.do/t/slug/12345/7
  → MainActivity（singleTop；已存在时走 onNewIntent）
       ├─ consumeDeepLink(intent)
       │    ├─ intent.action == ACTION_VIEW 才继续
       │    ├─ TopicLink.parse(intent.dataString)
       │    │    → Target(topicId = 12345, postNumber = 7)
       │    ├─ deepLinkTopicId = 12345; deepLinkPostNumber = 7
       │    └─ intent.data = null            // 防止旋转 / 重建重复处理
       └─ MainScreen LaunchedEffect(deepLinkTopicId, deepLinkPostNumber)
            ├─ openTopicId = 12345; openPostNumber = 7
            └─ onDeepLinkConsumed()          // 清空 Activity 字段
                 → TopicDetailScreen(initialPostNumber = 7)
                      → 首次加载从第 7 楼附近开始
                      → scrollToItem(第一个 postNumber >= 7 的位置 + 1)
```

`TopicLink` 是纯字符串实现，支持 `/t/{id}`、`/t/{slug}/{id}`、`/t/{slug}/{id}/{post}`、
`.json` 后缀、query / fragment、协议相对与省略 scheme 的写法。详见
[09-utilities.md](09-utilities.md#3-topiclink)。

---

## 7. 点赞与取消点赞（乐观更新）

```text
PostCard 点赞按钮 → vm.toggleLike(post)
  ├─ 读取当前 likedOf / likeCountOf（先看 interactions 覆盖，再看 post 原始值）
  ├─ 立即写 interactions[post.id] = PostInteraction(
  │      liked = !likedNow,
  │      likeCount = (countNow ± 1).coerceAtLeast(0)
  │  )                                         // UI 立即变化
  ├─ repo.toggleLike(post.id, likedNow)
  │    ├─ 已赞 → DELETE /post_actions/{postId}?post_action_type_id=2
  │    └─ 未赞 → POST /post_actions（id, post_action_type_id=2）
  │         └─ 用正则从响应中提取 actions_summary 里 id=2 的 count
  ├─ 成功且 count >= 0 → interactions 用服务端 count
  └─ 失败 → 回滚到 likedNow / countNow
```

**取舍**：

- 网络失败时用户看到的状态会回滚；
- 服务端返回结构变化导致正则取不到 count 时，保留本地推算值（结果可能偏差 ±1）；
- 点赞状态不写回 `Post` 原始字段，而是存在 `TopicState.interactions`，避免修改不可变模型。

---

## 8. 收藏 / 取消收藏

```text
TopAppBar 收藏按钮 → vm.toggleBookmark()
  ├─ bookmarking = true（防重入）
  ├─ repo.toggleBookmark(topicId, detail.bookmarked)
  │    ├─ 已收藏 → DELETE /bookmarks/{topicId}
  │    └─ 未收藏 → POST /bookmarks（bookmarkable_id, bookmarkable_type=Topic）
  ├─ 成功 → detail = detail.copy(bookmarked = !currentlyBookmarked)
  └─ 无论成败 → bookmarking = false
```

---

## 9. 回复话题

```text
某楼层「回复」→ vm.setReplyTo(post) → showComposer = true
  → ReplyComposer（bottomBar）输入 Markdown
  → 发送 → vm.sendReply(raw)
       ├─ raw.trim() 为空直接 return
       ├─ sending = true
       ├─ repo.reply(topicId, text, replyToPostNumber)
       │    └─ POST /posts（topic_id, raw, reply_to_post_number?）
       ├─ 成功 → sending=false, replyTo=null, onDone(true), load() 重新拉详情
       └─ 失败 → sendError = friendlyMessage(), onDone(false)
```

- 发送成功后重新 `load()`，不做局部插入，确保楼层号 / 排序 / 统计与服务端一致；
- `ReplyComposer` 使用 `imePadding()`，键盘弹出时输入框可见；
- 失败信息显示在输入框上方。

---

## 10. 发新帖

```text
话题列表 FAB「发帖」→ MainScreen.composing = true → ComposerScreen
  ├─ LaunchedEffect(Unit) → repository.categories()
  │    └─ 默认选中第一个顶级分类（parentCategoryId == null）
  ├─ 校验：标题 trim >= 4 字、正文 trim >= 10 字、已选分类、非发送中
  └─ 点击「发布」→ repository.createTopic(title, raw, categoryId)
       ├─ POST /posts（title, raw, category）
       ├─ 解析响应 topic_id；缺失则抛异常
       ├─ 成功 → onPosted()（回到列表）
       └─ 失败 → error = message
```

**现状**：正文是纯 Markdown 输入框，没有预览、没有附件、没有草稿自动保存；
`DraftStore` 已实现但未注入本页面。发布成功后也不会自动刷新列表（`onPosted` 仅关闭页面）。

---

## 11. 搜索

```text
输入框 onValueChange → SearchViewModel.updateQuery(q)
  ├─ 更新 query
  ├─ 取消上一个 searchJob
  ├─ q.trim().length < 2 → 清空结果、searched=false
  └─ searchJob = launch { delay(320); run(q) }     // 300ms 防抖

点击键盘搜索 / 历史词 → submit(q)
  └─ 取消旧任务 → 立即 run(q)

run(q)
  ├─ loading = true
  ├─ repo.search(term)            GET /search.json?q=…&page=1&include_blurbs=true
  ├─ 成功：
  │    ├─ historyStore.add(term)  // >= 2 字，最多 20 条
  │    ├─ results = resp.posts
  │    ├─ topics = resp.topics.associateBy { id }
  │    ├─ searched = true
  │    └─ error = resp.grouped?.error
  └─ 失败：loading=false, searched=true, error=message
```

界面状态：

| 条件 | UI |
|---|---|
| `loading` | 居中转圈 |
| `!searched` | 搜索历史（FlowRow 标签）或空提示 |
| 无结果 | `error` 或「没有找到相关内容」 |
| 有结果 | 每条显示话题标题、blurb 纯文本、作者 · 楼层 · 时间 |

点击结果 → `onOpenTopic(post.topicId)`。

---

## 12. 切换站点

**实际 UI 路径**：

```text
ProfileScreen「服务器地址」→ ServerDialog
  ├─ 保存时要求 startsWith("http")，否则不关闭弹窗
  └─ onSave(cleaned)
       ├─ sessionStore.baseUrl = cleaned        // trimEnd('/')
       ├─ sessionStore.clearCookies()           // 清 cookie / csrf / 用户信息
       ├─ repository.invalidateLists()
       └─ 弹窗关闭
```

**`MainViewModel.changeServer()` 是更完整的实现，但当前没有调用方**：

```text
changeServer(url, onDone)
  ├─ baseUrl = url.trimEnd('/')
  ├─ clearCookies()
  ├─ container.onSiteChanged()       // HttpClients.invalidate()
  ├─ repository.invalidateLists()
  ├─ booting = true, user = null
  ├─ repo.siteBasicInfo() 探测可用性 → ok
  ├─ boot()                          // 刷新标题 / 图标 / 用户 / 未读
  └─ onDone(ok)
```

**差异影响**：

- 通过 UI 切换后，下一次 API 调用会使用新的 `baseUrl`（`DiscourseApi` 的 provider 动态读取）；
- 但 `MainState.baseUrl` 未更新，`LocalSiteBaseUrl` 仍为旧值，头像 / 图片地址在重启前仍指向旧站点；
- `HttpClients` 按 baseUrl 新建客户端，旧客户端仍在缓存中，未造成错误但会占用一点资源；
- 顶栏标题 / 图标要等重新 `boot()` 才变化。

---

## 13. 通知

```text
进入通知 Tab → NotificationsScreen 组合
  └─ LaunchedEffect(Unit) → load()
       ├─ loading = true
       ├─ repo.notifications(offset = 0)      GET /notifications.json?offset=0
       ├─ 成功 → items = resp.notifications
       │         onRead()                     // MainViewModel.refreshUnread()
       │         └─ refreshSession() → 更新底部导航角标
       └─ 失败 → 已登录显示原始错误，未登录显示「登录后查看通知」

点击通知行（仅 topicId != null 可点）
  → onOpenTopic(topicId)
```

**通知类型映射**在 `NotificationsScreen.typeLabel()` 中硬编码为「中文标签 + emoji」，
覆盖提到、回复、引用、编辑、点赞、私信、徽章、聊天、关注动态等 id。

**现状**：

- 无 ViewModel，状态在 Composable 内，切走再回来会重新请求；
- 不分页（`load_more_notifications` 未使用）；
- 不使用 `markAllNotificationsRead()`，角标清空依赖 `refreshSession()` 的服务端未读计数；
- 未读行用浅色背景区分。

---

## 14. CSRF 写请求链路（网络层视角）

```text
Repository 发起写操作（postForm / putForm / deleteForm）
  → DiscourseApi.execute(req)
       ├─ isWrite = method !in (GET, HEAD)
       ├─ attachCsrf(req)                   // 无 X-CSRF-Token 且本地有 token 时补上
       ├─ client.newCall(req).enqueue(...)  // HeaderInterceptor 再补公共头 / Origin / Referer
       ├─ 2xx → 返回 body
       ├─ 403 且 isWrite 且允许重试 且 looksLikeCsrfFailure(body)
       │    ├─ before = csrfProvider()
       │    ├─ fresh = refreshCsrf()        // GET /session/csrf.json，落盘
       │    ├─ fresh != before → execute(req, allowCsrfRetry = false)
       │    └─ fresh == before → 抛出 ApiException(Kind.CSRF)
       └─ 其他状态码 → apiError(code, body) → ApiException(kind)
```

`HeaderInterceptor` 还会在响应头出现 `X-CSRF-Token` 时更新本地 token（Discourse 会轮换）。

测试覆盖：`app/src/test/java/org/linuxdo/android/data/remote/CsrfTest.kt`（详见 [12-testing.md](12-testing.md)）。

---

## 15. 流程与代码索引

| 流程 | 主要入口 | 关键数据层方法 |
|---|---|---|
| 启动 | `LinuxDoApp.onCreate`、`MainActivity.onCreate` | `siteBasicInfo`、`currentUserOrNull`、`refreshSession` |
| 登录 | `ProfileScreen.LoginCard` | `login`、`refreshCsrfToken` |
| 登出 | `ProfileScreen` | `logout`、`clearCookies` |
| 列表 | `FeedScreen`、`FeedViewModel` | `topicList`、`categories` |
| 详情 | `TopicDetailScreen`、`TopicDetailViewModel` | `topic`、`postsNear` |
| 点赞 | `TopicDetailViewModel.toggleLike` | `toggleLike` |
| 收藏 | `TopicDetailViewModel.toggleBookmark` | `toggleBookmark` |
| 回复 | `TopicDetailScreen.ReplyComposer` | `reply` |
| 发帖 | `ComposerScreen` | `createTopic` |
| 搜索 | `SearchScreen`、`SearchViewModel` | `search` |
| 通知 | `NotificationsScreen` | `notifications`、`refreshSession` |
| 深链 | `MainActivity.consumeDeepLink` | `TopicLink.parse`、`postsNear` |
| CSRF | `DiscourseApi.execute` | `refreshCsrf`、`CsrfTokens` |
