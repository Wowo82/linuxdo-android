# 08 · UI 层参考

UI 全部使用 Jetpack Compose（Material 3），没有 XML 布局、没有 Fragment。
本文按「入口 → 根界面 → 功能页 → 公共组件 → 主题」组织。

---

## 1. UI 结构总览

```text
MainActivity
  └─ LinuxDoTheme
       └─ MainScreen
            ├─ [整屏] TopicDetailScreen
            │         ├─ TopicHeader
            │         ├─ PostCard × N
            │         └─ ReplyComposer（bottomBar）
            ├─ [整屏] ComposerScreen
            └─ [Scaffold]
                 ├─ CenterAlignedTopAppBar
                 ├─ NavigationBar（4 个 Tab + 未读角标）
                 ├─ ExtendedFloatingActionButton（仅 Tab 0）
                 └─ Box
                      ├─ Tab 0 FeedScreen
                      ├─ Tab 1 SearchScreen
                      ├─ Tab 2 NotificationsScreen
                      └─ Tab 3 ProfileScreen
```

| 页面 | 文件 | 状态载体 |
|---|---|---|
| 根界面 | [MainScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/MainScreen.kt) | `remember` + `MainViewModel` |
| 话题列表 | [FeedScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedScreen.kt) | `FeedViewModel` |
| 话题详情 | [TopicDetailScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailScreen.kt) | `TopicDetailViewModel` |
| 发新帖 | [ComposerScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/ComposerScreen.kt) | 局部 `remember` |
| 搜索 | [SearchScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchScreen.kt) | `SearchViewModel` |
| 通知 | [NotificationsScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/notifications/NotificationsScreen.kt) | 局部 `remember` |
| 我的 | [ProfileScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/profile/ProfileScreen.kt) | 局部 `remember` |

---

## 2. MainActivity

文件：[MainActivity.kt](../../app/src/main/java/org/linuxdo/android/MainActivity.kt)

| 成员 | 说明 |
|---|---|
| `deepLinkTopicId: Long?` | `mutableStateOf`，深链目标话题 |
| `deepLinkPostNumber: Int?` | `mutableStateOf`，深链目标楼层 |
| `onCreate` | `enableEdgeToEdge()` → 取 `container` → `consumeDeepLink(intent)` → `setContent` |
| `onNewIntent` | `setIntent(intent)` + `consumeDeepLink(intent)`（`singleTop` 复用实例） |
| `consumeDeepLink(intent)` | 仅处理 `ACTION_VIEW`；`TopicLink.parse(dataString)`；写入状态后 `intent.data = null` 防重复 |

`setContent` 结构：

```kotlin
setContent {
    LinuxDoTheme {
        val vm: MainViewModel = viewModel(factory = MainViewModelFactory(container))
        val state by vm.state.collectAsState()
        CompositionLocalProvider(
            LocalSiteBaseUrl provides state.baseUrl.ifBlank { container.sessionStore.baseUrl },
        ) {
            MainScreen(
                vm = vm,
                state = state,
                container = container,
                deepLinkTopicId = deepLinkTopicId,
                deepLinkPostNumber = deepLinkPostNumber,
                onDeepLinkConsumed = { deepLinkTopicId = null; deepLinkPostNumber = null },
            )
        }
    }
}
```

**为什么用 CompositionLocal**：头像 / 图片地址需要跟随当前站点；`MainState.baseUrl` 变化会触发重组，
而直接读 `SharedPreferences` 既阻塞主线程，也无法被 Compose 观察到变化。

---

## 3. MainScreen

文件：[MainScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/MainScreen.kt)

### 3.1 参数

```kotlin
@Composable
fun MainScreen(
    vm: MainViewModel,
    state: MainState,
    container: AppContainer,
    deepLinkTopicId: Long? = null,
    deepLinkPostNumber: Int? = null,
    onDeepLinkConsumed: () -> Unit = {},
)
```

### 3.2 局部状态

| 状态 | 类型 | 作用 |
|---|---|---|
| `tab` | `Int` | 0 话题 / 1 搜索 / 2 通知 / 3 我的 |
| `openTopicId` | `Long?` | 非空时整屏显示详情 |
| `openPostNumber` | `Int?` | 详情初始楼层 |
| `composing` | `Boolean` | true 时整屏显示发帖页 |

### 3.3 渲染分支

```kotlin
if (openTopicId != null) {
    TopicDetailScreen(topicId, container, initialPostNumber = openPostNumber, onBack = { … })
    return
}
if (composing) {
    ComposerScreen(container, onDismiss = { composing = false }, onPosted = { composing = false })
    return
}
// 否则 Scaffold(...)
```

顶栏标题：

| 条件 | 标题 |
|---|---|
| 详情页打开中 | `""`（详情页自带 TopAppBar，MainScreen 不渲染） |
| Tab 0 | `state.siteTitle` |
| Tab 1 | 「搜索」 |
| Tab 2 | 「通知」 |
| Tab 3 | 「我的」 |

### 3.4 底部导航与 FAB

- `NavigationBar` 4 个 `NavigationBarItem`，图标来自 `Icons.Outlined`；
- 通知 Tab 在 `state.unreadNotifications > 0` 时用 `BadgedBox` 显示数字；
- 点击 Tab 0 时额外调用 `feedVm.ensureLoaded()`（懒加载兜底）；
- `ExtendedFloatingActionButton` 只在 Tab 0 显示，点击 `composing = true`，文案「发帖」；
- `Scaffold` 内容区包一层 `Surface`（`background` 色）+ `Box(fillMaxSize)`。

### 3.5 Tab 枚举

```kotlin
private enum class Tab(val label: String, val icon: ImageVector) {
    TOPICS("话题", Icons.Outlined.Explore),
    SEARCH("搜索", Icons.Outlined.Search),
    NOTIFICATIONS("通知", Icons.Outlined.Notifications),
    ME("我的", Icons.Outlined.Person),
}
```

### 3.6 `simpleFactory`

```kotlin
@Suppress("UNCHECKED_CAST")
inline fun <reified T : ViewModel> simpleFactory(
    crossinline creator: () -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
```

用于无参 ViewModel 或已由闭包捕获依赖的 ViewModel：

```kotlin
val feedVm: FeedViewModel = viewModel(
    key = "feed",
    factory = simpleFactory { FeedViewModel(container.repository) },
)
```

**位置**：目前定义在 `MainScreen.kt` 末尾；文档 `CONTRIBUTING.md` 把它列为技术债（应移到 `ui/common/`）。

### 3.7 各 Tab 的接线

| Tab | Composable | 回调 |
|---|---|---|
| 0 | `FeedScreen(vm = feedVm)` | `onOpenTopic { openTopicId = it.id }` |
| 1 | `SearchScreen(container)` | `onOpenTopic { id -> openTopicId = id }` |
| 2 | `NotificationsScreen(container)` | `onOpenTopic` 同上；`onRead { vm.refreshUnread() }` |
| 3 | `ProfileScreen(container, user, siteTitle)` | `onLoggedIn { vm.setUser(it); feedVm.refresh() }`；`onLogout { vm.logout() }` |

---

## 4. MainViewModel

文件：[MainViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/MainViewModel.kt)

### 4.1 `MainState`

| 字段 | 类型 | 默认 | 含义 |
|---|---|---|---|
| `booting` | `Boolean` | `true` | 启动加载中 |
| `baseUrl` | `String` | `""` | 当前站点，驱动 `LocalSiteBaseUrl` |
| `siteTitle` | `String` | `"LINUX DO"` | 顶栏标题 |
| `siteIcon` | `String?` | `null` | 站点图标（当前 UI 未直接使用） |
| `user` | `User?` | `null` | 当前用户 |
| `unreadNotifications` | `Int` | `0` | 底部角标 |
| `bootError` | `String?` | `null` | 站点不可达提示 |

### 4.2 方法

| 方法 | 行为 |
|---|---|
| `boot()` | `booting=true` → 拉站点信息 → 拉当前用户 → 补 CSRF → 拉未读数 → 更新 state |
| `refreshUnread()` | 未登录直接返回；否则 `refreshSession()` 后更新未读数 |
| `setUser(user)` | 登录成功后由 UI 调用，更新 `user` |
| `logout()` | `repo.logout()` → 清空 user / 未读 |
| `changeServer(url, onDone)` | **当前无调用方**；完整切换站点实现（见 03 §12） |
| `loadProfile(username)` | **当前无调用方**；`repo.user(username)` 的 `Result` 包装 |

`MainViewModelFactory`：

```kotlin
class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
}
```

---

## 5. Feed（话题列表）

文件：[FeedViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedViewModel.kt)、
[FeedScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedScreen.kt)

### 5.1 `FeedState`

| 字段 | 类型 | 默认 | 含义 |
|---|---|---|---|
| `filter` | `ListFilter` | `LATEST` | 当前列表类型 |
| `period` | `Period` | `WEEKLY` | 精华周期（默认周） |
| `categoryId` | `Long?` | null | 分类维度（UI 暂无分类切换入口） |
| `topics` | `List<Topic>` | 空 | 已加载话题 |
| `users` | `Map<Long, User>` | 空 | 用户 id → User |
| `categories` | `List<Category>` | 空 | 分类列表 |
| `loading` | `Boolean` | false | 首屏加载 |
| `refreshing` | `Boolean` | false | 下拉刷新 |
| `loadingMore` | `Boolean` | false | 加载下一页 |
| `endReached` | `Boolean` | false | 已到最后一页 |
| `error` | `String?` | null | 用户可读错误 |
| `page` | `Int` | 0 | 当前页码 |

### 5.2 `FeedViewModel`

| 方法 | 说明 |
|---|---|
| `ensureLoaded()` | `topics` 非空或正在加载则返回；否则 `load(false)` |
| `setFilter(filter, categoryId = 当前)` | 相同组合直接返回；否则清空列表、page=0、重新加载 |
| `setPeriod(period)` | 清空列表、page=0、重新加载 |
| `refresh()` | `load(refresh = true)`，绕过缓存 |
| `loadMore()` | 防重入；`page + 1`；成功合并去重；`endReached = incoming 为空 || moreTopicsUrl == null` |
| `load(refresh)` | 首次加载分类；`forceReload = refresh`；成功覆盖列表 |
| `categoryFor(topic)` | 按 `topic.categoryId` 在 `categories` 中查找 |
| `userFor(id)` | 从 `users` map 取 |

加载防护：

- `load(refresh=false)` 在 `loading || refreshing` 时直接返回；
- `loadMore()` 在 `loading || refreshing || loadingMore || endReached` 时返回；
- 分类只加载一次（`categoriesLoaded` 私有标记）；
- 合并时 `distinctBy { it.id }`，防止重复楼层 / 重复话题。

错误提示 `friendly()`（internal 扩展，文件末尾）：

| `ApiException.Kind` | 文案 |
|---|---|
| `NETWORK` | 网络连接失败，检查网络或试试关闭 DoH |
| `AUTH` | 需要登录后才能查看 |
| `RATE_LIMIT` | 请求太频繁，稍后再试 |
| `SERVER` | 服务器出错了 |
| 其他 / 非 ApiException | `message ?: "未知错误"` |

### 5.3 `FeedScreen` 结构

```text
Column
 ├─ FilterBar
 │   ├─ ScrollableTabRow：最新 / 热门 / 新帖 / 未读 / 精华
 │   └─ 仅「精华」显示周期行：日 / 周 / 月 / 季 / 年 / 全部
 ├─ HorizontalDivider
 └─ PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm.refresh)
     ├─ loading 且无数据 → CenterLoading()
     ├─ error 且无数据 → ErrorBox(message, onRetry = vm.refresh)
     ├─ 无数据 → EmptyBox("这里还没有内容")
     └─ LazyColumn(state = listState)
          ├─ items(state.topics, key = { it.id }) → TopicRow + 分割线
          ├─ loadingMore → CenterLoading(height = 64.dp)
          └─ endReached → 「已经到底啦」
```

无限滚动：

```kotlin
LaunchedEffect(listState) {
    snapshotOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { last -> if (last != null && last >= state.topics.size - 3) vm.loadMore() }
}
```

`TopicRow` 布局：

| 区域 | 内容 |
|---|---|
| 顶部标签行 | 置顶图钉 + 分类 `CategoryChip`（有则显示） |
| 标题 | `plainTitle`，最多 2 行，未读加粗 |
| 摘要 | `Html.toPlain(excerpt)`，最多 2 行，55% 透明度 |
| 右侧缩略图 | `bestThumb`，52dp，8dp 圆角 |
| 底部元信息 | 最后回复人头像 + 用户名、回复数、浏览量、相对时间 |

### 5.4 共享状态组件

同文件内提供，供详情页等复用：

```kotlin
@Composable internal fun CenterLoading(height: Dp = 200.dp)
@Composable internal fun ErrorBox(message: String, onRetry: () -> Unit)
@Composable internal fun EmptyBox(text: String)
```

### 5.5 ComposeShims

文件：[ComposeShims.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/ComposeShims.kt)

```kotlin
@Composable
fun <T> StateFlow<T>.collectState(): State<T> {
    val flow: Flow<T> = this
    return flow.collectAsState(this.value)
}

fun <T> snapshotOf(block: () -> T): Flow<T> = snapshotFlow(block)
```

`collectState()` 只是把 `StateFlow` 显式转成 `Flow` 再调用 `collectAsState`，
避免不同版本 API 的接收者歧义。

---

## 6. Topic（话题详情）

文件：[TopicDetailViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailViewModel.kt)、
[TopicDetailScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailScreen.kt)

### 6.1 `PostInteraction`

```kotlin
data class PostInteraction(val liked: Boolean, val likeCount: Int)
```

用于覆盖服务端返回，承载乐观更新结果。

### 6.2 `TopicState`

| 字段 | 类型 | 含义 |
|---|---|---|
| `detail` | `TopicDetail?` | 话题元数据 |
| `posts` | `List<Post>` | 已加载楼层（按 postNumber 升序） |
| `interactions` | `Map<Long, PostInteraction>` | post.id → 本地点赞状态 |
| `loading` | `Boolean` | 首屏加载 |
| `error` | `String?` | 加载错误 |
| `sending` | `Boolean` | 回复发送中 |
| `sendError` | `String?` | 回复错误 |
| `replyTo` | `Post?` | 当前回复目标 |
| `bookmarking` | `Boolean` | 收藏请求中 |
| `jumping` | `Boolean` | 正在按楼层定位 |

方法：

```kotlin
fun likedOf(post: Post): Boolean = interactions[post.id]?.liked ?: post.liked
fun likeCountOf(post: Post): Int = interactions[post.id]?.likeCount ?: post.likeCount
```

### 6.3 `TopicDetailViewModel`

构造：

```kotlin
class TopicDetailViewModel(
    private val repo: UserRepository,
    private val topicId: Long,
    private val initialPostNumber: Int? = null,
) : ViewModel()
```

内部字段：`loadedPostIds: MutableSet<Long>`（去重）、`loadingMore: Boolean`（防重入）。

| 方法 | 说明 |
|---|---|
| `init` | 立即 `load(initialPostNumber)` |
| `load(fromPostNumber = null)` | 有楼层号 → `postsNear`（失败回退 `topic`）；否则 `topic`；清空并重建 `loadedPostIds`；按 postNumber 排序 |
| `refresh()` | `load()`（从第一楼） |
| `loadMore()` | 终止条件：正在加载 / `posts.size >= postsCount` / next > highestPostNumber；`next = 最大 postNumber + 1`；过滤已加载 id 后追加 |
| `setReplyTo(post?)` | 设置 / 清除回复目标 |
| `sendReply(raw, onDone)` | 空文本忽略；`repo.reply`；成功清 replyTo、回调、`load()`；失败写 `sendError` |
| `toggleLike(post)` | 乐观更新 → 请求 → 成功用服务端 count / 失败回滚 |
| `toggleBookmark()` | 防重入；成功把 `detail.bookmarked` 取反 |
| `markRead(postNumber)` | 调用 `repo.markTopicRead`；**UI 未调用** |

`friendlyMessage()`（internal 扩展，文件末尾）：与 `friendly()` 类似，但文案更短
（「网络连接失败」「需要登录」「操作太频繁」「服务器错误」）。

### 6.4 `TopicDetailScreen` 结构

```text
TopicDetailScreen(topicId, container, initialPostNumber, onBack)
  ├─ viewModel(key = "topic_{topicId}_{initialPostNumber ?: 0}")
  ├─ remember: listState / draft / showComposer / jumped
  ├─ LaunchedEffect(state.posts, state.jumping)：按 initialPostNumber 定位楼层
  ├─ LaunchedEffect(listState)：接近底部时 loadMore
  └─ Scaffold
       ├─ TopAppBar
       │    ├─ 标题：detail.plainTitle ?: "话题"
       │    ├─ 返回箭头 → onBack
       │    └─ 收藏按钮（detail.bookmarked 决定图标与颜色）
       ├─ bottomBar：showComposer 时显示 ReplyComposer
       └─ Box
            ├─ 加载 / 错误 / 空态
            ├─ LazyColumn
            │    ├─ item("header") TopicHeader（标题、标签、回复/浏览/赞）
            │    ├─ items(posts, key = id) PostCard
            │    └─ item("footer")「已经到底啦」/「加载中…」
            └─ 非输入状态：底部「回复」胶囊按钮
```

楼层定位：

```kotlin
LaunchedEffect(state.posts, state.jumping) {
    val target = initialPostNumber ?: return@LaunchedEffect
    if (jumped || state.jumping || state.posts.isEmpty()) return@LaunchedEffect
    val index = state.posts.indexOfFirst { it.postNumber >= target }
    if (index >= 0) {
        listState.scrollToItem(index + 1)   // +1 为 header
        jumped = true
    }
}
```

`PostCard` 组成：

| 区域 | 内容 |
|---|---|
| 头部 | 头像（`AvatarFallback` 垫底 + `AsyncImage`）、`displayName`、`userTitle`、相对时间（`version > 1` 加「· 已编辑」）、`#postNumber` |
| 回复引用 | `replyToUser` 非空时显示「回复 xxx」灰底条 |
| 正文 | `HtmlContent(post.cooked)`，15sp |
| 操作行 | 点赞（`Favorite` / `FavoriteBorder` + 数量）、回复图标 |

`ReplyComposer`：

- 顶部提示「回复 #N 用户名」（有 replyTo 时）；
- `TextField` 高 120dp，提示「说点什么…（支持 Markdown）」；
- 错误文案；
- 「取消」「发送」按钮；发送中显示 14dp 进度圈；
- `imePadding()` 适配键盘。

---

## 7. Composer（发新帖）

文件：[ComposerScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/ComposerScreen.kt)

### 7.1 局部状态

| 状态 | 类型 | 说明 |
|---|---|---|
| `title` | `String` | 标题，最多 120 字 |
| `body` | `String` | Markdown 正文 |
| `categories` | `List<Category>` | 来自 `repository.categories()` |
| `selected` | `Category?` | 默认第一个顶级分类 |
| `sending` | `Boolean` | 发布中 |
| `error` | `String?` | 发布错误 |

### 7.2 校验与提交

```kotlin
val canPost = title.trim().length >= 4 &&
              body.trim().length >= 10 &&
              selected != null && !sending
```

点击「发布」：

```kotlin
container.repository.createTopic(
    title = title.trim(),
    raw = body.trim(),
    categoryId = selected!!.id,
).onSuccess { onPosted() }
 .onFailure { error = it.message ?: "发布失败" }
```

### 7.3 布局

- 顶栏：关闭按钮 + 右侧「发布」（不可发布时 30% 透明度且无点击）；
- 标题 `TextField`（单行、限 120 字）；
- 「选择分类」+ `FlowRow` 顶级分类 `FilterChip`；
- 正文 `OutlinedTextField`（280dp 高，Markdown）；
- 错误文案、发布中提示；
- 整页 `verticalScroll` + `imePadding`。

**注意**：

- `categories.filter { c -> c.readRestricted.not() || true }` 是恒真条件，等价于不过滤；
- 没有草稿保存、预览、附件上传；
- 发布成功后只关闭页面，不主动刷新列表（列表下拉或下次进入时更新）。

---

## 8. Search（搜索）

文件：[SearchViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchViewModel.kt)、
[SearchScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchScreen.kt)

### 8.1 `SearchState`

| 字段 | 类型 | 含义 |
|---|---|---|
| `query` | `String` | 当前输入 |
| `results` | `List<SearchPost>` | 命中楼层 |
| `topics` | `Map<Long, Topic>` | topicId → 话题，用于显示标题 |
| `history` | `List<String>` | 搜索历史 |
| `loading` | `Boolean` | 搜索中 |
| `searched` | `Boolean` | 是否已执行过搜索 |
| `error` | `String?` | 错误 / 服务端搜索错误 |

### 8.2 `SearchViewModel`

| 方法 | 说明 |
|---|---|
| `init` | 读取历史到 state |
| `updateQuery(q)` | 更新 query；取消旧 `searchJob`；长度 < 2 清空结果；否则 `delay(320)` 后搜索 |
| `submit(q)` | 取消旧任务，立即搜索 |
| `run(q)`（private） | 长度 < 2 返回；`repo.search(term)`；成功写历史、结果、话题 map；失败写 error |
| `clearHistory()` | 清空 DataStore 与 state |
| `topicTitleFor(topicId)` | 从 topics map 取纯标题 |

防抖是「320ms delay + Job 取消」，不是 `Flow.debounce`；实现简单，满足单输入场景。

### 8.3 `SearchScreen`

```text
Column
 ├─ Surface
 │   └─ TextField（前置搜索图标、清空按钮、IME Search）
 └─ when
      ├─ loading → 居中进度
      ├─ !searched → HistoryList（历史 FlowRow，或空提示）
      ├─ results.isEmpty() → error ?: "没有找到相关内容"
      └─ LazyColumn
           └─ 每条：话题标题（若有）、blurb 纯文本（3 行）、
                   作者 · #楼层 · 相对时间
```

- 历史最多 20 条，点击直接 `submit(term)`；
- 清空按钮同时清输入与 `updateQuery("")`；
- 结果中的 blurb 含 `<span class="search-highlight">`，经 `Html.toPlain()` 后高亮丢失。

---

## 9. Notifications（通知）

文件：[NotificationsScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/notifications/NotificationsScreen.kt)

### 9.1 状态

全部为 Composable 内 `remember`：

| 状态 | 类型 |
|---|---|
| `items` | `List<Notification>` |
| `loading` | `Boolean`（初始 true） |
| `error` | `String?` |
| `scope` | `rememberCoroutineScope()` |

内部 `suspend fun load()`：`repository.notifications()` → 成功写入 items 并调用 `onRead()`；
失败时若已登录显示原始 message，否则显示「登录后查看通知」。

`LaunchedEffect(Unit) { load() }`，因此每次进入 Tab 都会刷新。

### 9.2 类型映射 `typeLabel(type: Int): Pair<String, String>`

返回「中文标签 + 图标」。图标是 emoji 字符，具体字符见 `typeLabel()` 源码；下表只列中文标签，便于检索：

| id | 标签 |
|---|---|
| 1 | 提到了你 |
| 2 | 回复了你 |
| 3 | 引用了你 |
| 4 | 编辑了你的帖子 |
| 5 | 赞了你 |
| 6, 7 | 私信 |
| 8 | 邀请已接受 |
| 9 | 发帖了 |
| 10 | 移动了帖子 |
| 11 | 链接到 |
| 12 | 获得徽章 |
| 13 | 邀请你加入话题 |
| 15 | 群组提到 |
| 16 | 群组消息 |
| 17 | 关注的首帖 |
| 18 | 话题提醒 |
| 19 | 赞（合并） |
| 24 | 书签提醒 |
| 25 | 回应 |
| 29, 30, 31, 32, 33, 40 | 聊天 |
| 800, 801, 802 | 关注动态 |
| 其他 | 通知 |

### 9.3 行渲染

- 左侧 30dp 圆形 emoji 容器；
- 标签（primary 色）+ 合并数量 `×N`；
- 标题（`Notification.title`，最多 2 行）；
- `@displayUsername`（有则显示）；
- 右侧相对时间；
- 未读行使用 `primary.copy(alpha = 0.045f)` 背景；
- `topicId != null` 才可点击 → `onOpenTopic(topicId)`；
- 列表底部「已读到最新」。

**现状**：无 ViewModel、无分页、无下拉刷新、「全部已读」接口未调用。

---

## 10. Profile（我的）

文件：[ProfileScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/profile/ProfileScreen.kt)

### 10.1 页面结构

```text
ProfileScreen(container, user, siteTitle, onLoggedIn, onLogout)
  ├─ Column(verticalScroll)
  │    ├─ user == null → LoginCard
  │    │   else        → ProfileCard
  │    ├─ 设置组
  │    │    ├─ 服务器地址：container.sessionStore.baseUrl → 打开 ServerDialog
  │    │    └─ 关于：LINUX DO Android 1.0.0 · 非官方客户端
  │    └─ 已登录时：退出登录
  └─ serverDialog 为 true → ServerDialog
```

### 10.2 `LoginCard`

| 项 | 说明 |
|---|---|
| 顶部 | 64dp 圆形站点首字（`AvatarFallback`） |
| 文案 | 「登录 {siteTitle}」+「使用社区账号登录，凭据只保存在本机加密存储」 |
| 输入 | 用户名或邮箱、密码（`PasswordVisualTransformation`，IME Done） |
| 校验 | 任一为空 → 「用户名和密码都不能为空」 |
| 提交 | `container.repository.login(login, password)` → 成功 `onLoggedIn(u)`，失败显示 message |
| 按钮 | 登录中显示 16dp 进度圈 |

### 10.3 `ProfileCard`

- 56dp 头像（`avatarUrlOf(user.avatarTemplate, 192)` + `AvatarFallback` 兜底）；
- `displayName`、`@username`；
- 标签：管理员（admin 或 moderator）、`mainFlair`、`TL{trustLevel}`；
- `bioExcerpt`；
- 统计：阅读时长（`timeRead / 60` 分钟）、徽章数、主页访问量；
- 「注册于 yyyy-MM-dd · 最近活跃 x 前」。

### 10.4 设置项

`SettingRow(label, value, onClick?)`：

- 服务器地址：值文本 + 可点击；
- 关于：纯展示。

退出登录：红色 `Logout` 图标 + 「退出登录」→ `onLogout()`。

### 10.5 `ServerDialog`

```kotlin
AlertDialog(
    title = "服务器地址",
    text = 说明 + OutlinedTextField(current),
    confirmButton = TextButton("保存") {
        val cleaned = text.trim().trimEnd('/')
        if (cleaned.startsWith("http")) onSave(cleaned)
    },
    dismissButton = TextButton("取消"),
)
```

`onSave` 的实际行为（由 `ProfileScreen` 提供）：

```kotlin
sessionStore.baseUrl = url
sessionStore.clearCookies()
repository.invalidateLists()
```

**已知不一致**：没有调用 `container.onSiteChanged()`，没有刷新 `MainState`。
详见 02 §10 与 03 §12。

---

## 11. Common（公共组件）

### 11.1 `HtmlContent`

文件：[HtmlText.kt](../../app/src/main/java/org/linuxdo/android/ui/common/HtmlText.kt)

```kotlin
@Composable
fun HtmlContent(
    html: String?,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
    maxLines: Int = Int.MAX_VALUE,
)
```

处理流程：

```text
html 为空 → 不渲染

预处理器（remember(html)）：
  <img ...>                  → 删除
  <video>...</video>         → “[视频]”
  <iframe>...</iframe>       → “[嵌入内容]”
  <pre ...>                  → <pre>
  HtmlCompat.fromHtml(..., FROM_HTML_MODE_COMPACT)

渲染：
  AndroidView(TextView)
    ├─ setTextIsSelectable(true)
    ├─ movementMethod = LinkMovementMethod
    ├─ autoLinkMask = Linkify.WEB_URLS
    ├─ lineSpacing 1.25
    └─ update：字号 / maxLines / 文字色 / 链接色 / runCatching { text = body }
```

选择 `TextView` 而非 `WebView` 的原因见 02 §9；不支持 img / table / iframe / onebox / 视频。

### 11.2 `CollapsibleHtml`

```kotlin
@Composable
fun CollapsibleHtml(
    html: String?,
    collapsedLines: Int = 8,
    expanded: Boolean,
    onToggle: () -> Unit,
)
```

折叠时 `maxLines = collapsedLines`，下方显示「展开全文」。**当前无调用方**。

### 11.3 `SiteUrl`

文件：[SiteUrl.kt](../../app/src/main/java/org/linuxdo/android/ui/common/SiteUrl.kt)

```kotlin
val LocalSiteBaseUrl = compositionLocalOf { BuildConfig.DEFAULT_BASE_URL }

@Composable fun siteBaseUrl(): String = LocalSiteBaseUrl.current

@Composable fun absoluteImageUrl(url: String?): String? = absolutize(url, siteBaseUrl())

@Composable fun avatarUrlOf(template: String?, size: Int): String? =
    avatarUrl(template, size, siteBaseUrl())
```

使用场景：

- `FeedScreen` 缩略图 → `absoluteImageUrl(topic.bestThumb)`；
- `TopicDetailScreen` / `ProfileScreen` 头像 → `avatarUrlOf(template, 96/192)`。

---

## 12. Theme（主题）

### 12.1 `LinuxDoTheme`

文件：[Theme.kt](../../app/src/main/java/org/linuxdo/android/ui/theme/Theme.kt)

```kotlin
@Composable
fun LinuxDoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
)
```

- 强调色：`Accent = #33D17A`（暗色主色）、`AccentDark = #2BB86A`（亮色主色）；
- 亮色：背景 `#F7F8F9`、表面 `#FFFFFF`、surfaceVariant `#F0F2F4`、outline `#D6DADE`、error `#D9453B`；
- 暗色：背景 `#0F1115`、表面 `#16191D`、surfaceVariant `#1E2227`、outline `#2E343A`、error `#FF6B5E`；
- `dynamicColor` 默认 false；仅当显式开启且 SDK >= S 时使用 Material You；
- 跟随系统深色：`isSystemInDarkTheme()`。

### 12.2 基础组件

文件：[Components.kt](../../app/src/main/java/org/linuxdo/android/ui/theme/Components.kt)

| 组件 / 函数 | 行为 |
|---|---|
| `parseHexColor(hex, fallback = #8699A2)` | 去 `#`，6 位 hex → `Color`；非法返回 fallback |
| `onColorFor(bg)` | 亮度 `0.299R + 0.587G + 0.114B`；> 0.6 用 `#111111`，否则白色 |
| `AvatarFallback(name, size = 36)` | 名字 hash → 色相；HSL 渐变圆 + 首字母；离线 / 加载失败兜底 |
| `CategoryChip(name, color, modifier)` | 4dp 圆角、16% 透明背景、11sp 文本 |
| `defaultCategoryColor()` | `#8699A2` |

`AvatarFallback` 细节：

```kotlin
val seed = name.hashCode() and 0x7fffffff
val hue = (seed % 360).toFloat()
val start = Color.hsl(hue, 0.55f, 0.62f)
val end = Color.hsl((hue + 40f) % 360f, 0.55f, 0.48f)
// Brush.linearGradient(start, end) + name.take(1).uppercase()
```

`onColorFor()` 与 `Layout` 无直接调用（当前未使用），保留给分类色前景计算。

---

## 13. 状态与生命周期小结

| 场景 | 行为 |
|---|---|
| 旋转屏幕 | Activity 重建；`MainViewModel` 与 `viewModel(key=…)` 的 VM 存活；`remember` 状态丢失（未用 `rememberSaveable`） |
| 切 Tab | Feed / Search VM 状态保留；通知 / 我的重新组合，按 `LaunchedEffect(Unit)` 重新加载（通知） |
| 关闭详情 | `openTopicId = null`，详情 Composable 离开组合；`TopicDetailViewModel` 仍在 Activity ViewModelStore 中 |
| 再次打开同一话题 | `viewModel(key = "topic_{id}_{post}")` 命中旧实例，可能直接显示旧数据（不会重新 init） |
| 发帖页关闭 | `composing = false`，标题 / 正文丢失 |
| 深链重复 | `intent.data = null` 防止旋转后重复打开 |

**Compose 写法约定**：

- `val state by vm.state.collectState()`，不要每帧 `collectAsState().value`；
- 可空值在 lambda 中无法智能转换，使用 `.orEmpty()` / `?:`；
- 长列表全部带稳定 key；
- 回调命名 `onXxx`；
- 私有子组件用 `private fun`。

---

## 14. UI 层已知限制

| 限制 | 影响 | 位置 |
|---|---|---|
| 无 `NavHost` / `BackHandler` | 系统返回键直接退出 App；无返回栈 | `MainScreen` |
| VM 为 Activity 作用域 | 复开话题可能显示旧数据 | `TopicDetailScreen` 的 `viewModel` |
| 无 `rememberSaveable` | 旋转后 Tab / 输入 / 弹窗状态丢失 | `MainScreen`、`ComposerScreen` |
| 正文图片被剥离 | 图片附件不可见 | `HtmlContent` |
| 无编辑 / 附件 / 草稿入口 | 相关 API 闲置 | `TopicDetailScreen`、`ComposerScreen` |
| 通知不分页、不标记已读 | 超过一页看不到；角标依赖会话刷新 | `NotificationsScreen` |
| 搜索结果高亮丢失 | blurb 高亮不可见 | `Html.toPlain` |
| 切换站点后根状态不刷新 | 标题 / 图标 / 图片域名仍是旧站点 | `ProfileScreen.ServerDialog` |
| 登录无 2FA | 开启两步验证的账号无法登录 | `UserRepository.login` |
