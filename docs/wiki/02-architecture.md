# 02 · 整体架构

## 1. 架构全景

```text
┌──────────────────────────────────────────────────────────────────────┐
│ UI 层  app/src/main/java/org/linuxdo/android/ui/                      │
│                                                                      │
│  MainActivity                                                         │
│    └─ LinuxDoTheme → MainScreen                                       │
│         ├─ Tab 0  FeedScreen            + FeedViewModel               │
│         ├─ Tab 1  SearchScreen          + SearchViewModel             │
│         ├─ Tab 2  NotificationsScreen   （Composable 局部状态）        │
│         ├─ Tab 3  ProfileScreen         （Composable 局部状态）        │
│         ├─ TopicDetailScreen            + TopicDetailViewModel         │
│         └─ ComposerScreen               （Composable 局部状态）        │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ suspend 调用 / StateFlow
┌──────────────────────────────▼───────────────────────────────────────┐
│ 数据层  data/                                                         │
│   UserRepository   业务编排、列表缓存、登录态维护                       │
│   SessionStore     站点地址 + 用户信息 + 加密 cookie / CSRF            │
└───────────────┬───────────────────────────────┬──────────────────────┘
                │                               │
┌───────────────▼──────────────────┐  ┌─────────▼──────────────────────┐
│ 远程 data/remote/                 │  │ 本地 data/local/               │
│  DiscourseApi   URL/请求/CSRF重试 │  │  DraftStore（DataStore）       │
│  HttpClients    按站点缓存客户端   │  │  SearchHistoryStore（DataStore）│
│  HeaderInterceptor  必需请求头    │  └────────────────────────────────┘
│  PersistentCookieJar Cookie 持久化│
│  CsrfTokens     token 内存缓存    │
│  Doh            DoH 解析（未启用）│
└───────────────┬──────────────────┘
                │ HTTPS（OkHttp）
┌───────────────▼──────────────────────────────────────────────────────┐
│ Discourse REST API（默认 https://linux.do，可切换自建站点）             │
└──────────────────────────────────────────────────────────────────────┘
```

**核心事实**：整个 App 只有一个业务仓库 `UserRepository`、一个 API 封装 `DiscourseApi`、
一个可变的网络客户端族（按 `baseUrl` 缓存）。没有 Retrofit、没有 DI 框架、没有数据库。

## 2. 分层与依赖规则

### 2.1 分层职责

| 层 | 包 | 职责 | 不应做的事 |
|---|---|---|---|
| 入口 / 装配 | `org.linuxdo.android` | Application、Activity、依赖容器 | 业务逻辑 |
| UI | `ui/**` | 组合界面、收集状态、触发用户意图 | 直接发 HTTP、直接操作加密 cookie / CSRF（读取站点地址是例外） |
| 业务 | `data`（`UserRepository`） | 端点编排、缓存、登录态更新、错误语义 | 持有 Compose 状态 |
| 远程 | `data/remote` | URL 拼接、请求执行、请求头、CSRF、Cookie | 业务判断（如「已登录」「收藏成功」） |
| 本地 | `data/local`、`SessionStore` | 持久化键值与序列化格式 | 网络访问 |
| 模型 | `data/model` | `@Serializable` 数据结构与只读派生属性 | Android / 网络依赖 |
| 工具 | `util` | 纯函数：HTML、时间、深链解析 | 依赖 UI 或网络 |

### 2.2 实际依赖方向

```text
MainActivity / LinuxDoApp / AppContainer
        │
        ├──────────────► ui.*  ──────────────► data（Repository / Store）
        │                  │                        │
        │                  ├──► util                 ├──► data.model
        │                  ├──► ui.common / theme     ├──► data.remote（ApiException、CsrfTokens、Jar）
        │                  └──► data.remote.*（仅 ApiException）              
        │                                           └──► data.local
        └──────────────► data.*
```

规则与例外：

1. `ui/*` 依赖 `data`、`util`、`ui.common`、`ui.theme`；
   为把 `ApiException.kind` 转成用户提示，`FeedViewModel` 与 `TopicDetailViewModel` 会 import
   `org.linuxdo.android.data.remote.ApiException`。
2. `data/UserRepository` 依赖 `data/remote`（`DiscourseApi`、`ApiException`）与 `data/model`。
3. `data/model` 只依赖 `kotlinx.serialization`，**不依赖 `data/remote`**。
4. `data/local` 与 `data/remote` 互不依赖；二者都由 `AppContainer` 组装。
5. `util/TopicLink`、`util/Html`、`util/TimeFmt` 均为纯 JVM 逻辑（`TopicLink` 刻意不用 `android.net.Uri`），
   因此可直接在 JVM 单测中运行。

更细的包级矩阵见 [04-module-reference.md](04-module-reference.md#3-包依赖矩阵)。

## 3. 应用启动链路

```text
Android 启动进程
  │
  ├─ LinuxDoApp.onCreate()
  │    └─ container = AppContainer(this)
  │         ├─ sessionStore        = SessionStore(context)
  │         ├─ imageHttpClient     = OkHttpClient（不挂 cookie，独立连接池）
  │         ├─ api                 = DiscourseApi(4 个 lambda provider)
  │         ├─ repository          = UserRepository(api, sessionStore)
  │         ├─ draftStore          = DraftStore(applicationContext)
  │         └─ searchHistoryStore  = SearchHistoryStore(applicationContext)
  │
  └─ MainActivity.onCreate()
       ├─ enableEdgeToEdge()
       ├─ consumeDeepLink(intent)              // 解析 /t/ 链接，写入 mutableState
       └─ setContent {
            LinuxDoTheme {
              val vm = viewModel(factory = MainViewModelFactory(container))
              val state by vm.state.collectAsState()
              // vm.init → boot()               // 见下
              CompositionLocalProvider(
                LocalSiteBaseUrl provides state.baseUrl
              ) { MainScreen(...) }
            }
          }
```

`MainViewModel.boot()` 的请求顺序（同一协程内串行）：

```text
1. repo.siteBasicInfo()              GET /site/basic-info.json
                                    成功 → 缓存 title / icon 到 SessionStore 与 MainState
2. repo.currentUserOrNull()          GET /session/current.json，403 视为未登录 → null
3. 若已登录且本地无 CSRF token        repo.refreshCsrfToken() → GET /session/csrf.json
4. repo.refreshSession()             再取一次 all_unread_notifications_count
                                    失败 → 未读数按 0 处理
5. 更新 MainState(booting=false, baseUrl, siteTitle, siteIcon, user, unread, bootError)
```

首次启动的同步 IO：

- `SessionStore.baseUrl` 读取 SharedPreferences（`linuxdo.prefs`）：`AppContainer` 构造时创建
  `SessionStore`，`baseUrl` 在 `MainViewModel` 构造时首次读取；
- `SessionStore.cookieJar` / `csrfTokens` 是 `lazy`，首次访问时才会创建 `EncryptedSharedPreferences`
  并同步读出落盘的 cookie / token。

## 4. 导航模型

项目**没有使用 Navigation Compose**，也没有 Fragment。`MainScreen` 用三个 `remember` 状态决定渲染内容：

| 状态 | 类型 | 作用 |
|---|---|---|
| `tab` | `Int`（0..3） | 底部导航当前页：话题 / 搜索 / 通知 / 我的 |
| `openTopicId` | `Long?` | 非 null 时整屏渲染 `TopicDetailScreen`，并隐藏 Scaffold |
| `openPostNumber` | `Int?` | 深链目标楼层（当前只有深链会设置） |
| `composing` | `Boolean` | true 时整屏渲染 `ComposerScreen` |

渲染优先级：

```text
openTopicId != null  → TopicDetailScreen（自带 Scaffold 与顶栏）
composing            → ComposerScreen（自带 Scaffold 与顶栏）
否则                 → Scaffold（CenterAlignedTopAppBar + NavigationBar + FAB + 当前 Tab）
```

行为特征：

- 详情页返回：只有顶栏返回箭头调用 `onBack` 把 `openTopicId` 置空；
  代码中没有 `BackHandler`，系统返回键不会先关详情页。
- 搜索、通知、我的里的条目通过 `onOpenTopic(topicId)` 打开详情；
  通知、搜索、我的都只传 `topicId`，不会定位到具体楼层；`openPostNumber` 目前只由深链设置。
- 底部 Tab 切换不会重建 ViewModel：`FeedViewModel`、`SearchViewModel` 都绑定在 Activity 的
  `ViewModelStore` 上（`viewModel(key = ...)` 没有 NavBackStackEntry 作用域）。

## 5. 状态管理

### 5.1 统一模式

```kotlin
data class XxxState(
    val data: List<Item> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class XxxViewModel(private val repo: UserRepository) : ViewModel() {
    private val _state = MutableStateFlow(XxxState())
    val state: StateFlow<XxxState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.fetch() }
                .onSuccess { d -> _state.update { it.copy(data = d, loading = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.friendly()) } }
        }
    }
}
```

Compose 侧统一通过 `ui/browse/ComposeShims.kt` 的扩展简化收集：

```kotlin
@Composable
fun <T> StateFlow<T>.collectState(): State<T> = (this as Flow<T>).collectAsState(value)
```

使用方式：`val state by vm.state.collectState()`。

### 5.2 状态归属

| 状态 | 持有者 | 生命周期 | 说明 |
|---|---|---|---|
| 站点标题 / 图标 / 当前用户 / 未读数 / 启动错误 | `MainViewModel` | Activity | 根级共享 |
| 话题列表、筛选、分类、分页 | `FeedViewModel` | Activity（key=`feed`） | 所有筛选 Tab 共用一个实例 |
| 详情、楼层、点赞覆盖、回复草稿状态 | `TopicDetailViewModel` | Activity（key=`topic_{id}_{post}`） | 每打开一个话题/楼层组合一个实例 |
| 搜索词、结果、历史 | `SearchViewModel` | Activity（key=`search`） | 切 Tab 不丢结果 |
| 通知列表、加载态、错误 | `NotificationsScreen` 局部 `remember` | 组合 | 无 ViewModel，离开页面即丢 |
| 登录表单、资料、服务器弹窗 | `ProfileScreen` 局部 `remember` | 组合 | 同上 |
| 发帖标题/正文/分类 | `ComposerScreen` 局部 `remember` | 组合 | 关闭即丢弃（未接 `DraftStore`） |
| 底部 Tab / 当前详情 / 是否发帖 | `MainScreen` 局部 `remember` | 组合 | 无进程恢复（未用 `rememberSaveable`） |
| 深链目标 | `MainActivity` 字段 + `MainScreen` 局部 | Activity / 组合 | 消费后置空 |

### 5.3 Compose 使用约定

- 长列表统一 `items(list, key = { it.id })`；
- 有状态参数以 `on` 开头（`onOpenTopic`、`onBack`、`onRead`）；
- 委托属性 `by` 取出的可空值在 lambda 中不能智能转换，因此出现 `state.error.orEmpty()` 这类写法；
- 加载 / 错误 / 空态复用 `ui/browse/FeedScreen.kt` 中的 `CenterLoading`、`ErrorBox`、`EmptyBox`。

## 6. 数据流

### 6.1 读：话题列表

```text
FeedScreen
  └─ LaunchedEffect(Unit) → FeedViewModel.ensureLoaded()
       └─ load(refresh = false)
            ├─ 首次：repo.categories()                    GET /categories.json
            └─ repo.topicList(filter, page=0, categoryId, period)
                 ├─ listCacheKey 命中且非 forceReload → 直接返回缓存
                 └─ 未命中 → GET /{filter}.json?page=0&per_page=30
                              （分类维度：/c/{id}/{filter}.json）
                      └─ json.decodeFromString<TopicListResponse>()
                           └─ 写入 LRU 缓存（上限 30 组）
      → FeedState(topics, users, categories, page, endReached)
      → Compose 重组

下滑到底
  └─ snapshotFlow(lastVisibleIndex) → loadMore()
       └─ topicList(page = page + 1)（走缓存）
            └─ 合并去重（distinctBy id），endReached = more_topics_url == null
```

### 6.2 读：话题详情

```text
TopicDetailScreen
  └─ viewModel(key = "topic_{id}_{post ?: 0}") → TopicDetailViewModel.init → load(initialPostNumber)
       ├─ 无楼层：repo.topic(id)              GET /t/{id}.json?include_raw=true
       └─ 有楼层：repo.postsNear(id, n)        GET /t/{id}.json?post_number=n
                  失败回退 repo.topic(id)
       → loadedPostIds = 本次返回的所有 post.id
       → TopicState(posts = 按 postNumber 排序, detail, loading=false)

下滑到底
  └─ loadMore()
       ├─ 终止条件：posts.size >= postsCount / next > highestPostNumber / 正在加载
       ├─ next = 当前最大 postNumber + 1
       └─ repo.postsNear(id, next)
            → 过滤 loadedPostIds → 追加 → 按 postNumber 排序
```

**为什么用 `post_number` 而不是 `page`**：Discourse 的帖子流是稀疏的，
`post_stream.stream` 可能包含已删除楼层，按 `page * 20` 会错位。

### 6.3 写：一次回复

```text
TopicDetailScreen.ReplyComposer.onSend
  → TopicDetailViewModel.sendReply(raw)
      ├─ state.sending = true
      ├─ repo.reply(topicId, raw, replyToPostNumber)
      │    └─ api.postForm("/posts", form)
      │         └─ DiscourseApi.execute
      │              ├─ attachCsrf（补 X-CSRF-Token）
      │              ├─ HeaderInterceptor（Accept / XHR / Origin / Referer / UA）
      │              ├─ 403 且像 CSRF 失败 → refreshCsrf() → 重放一次
      │              └─ 2xx 返回 JSON 字符串
      ├─ 成功：清空 replyTo、回调 onDone(true)、load() 重新拉详情
      └─ 失败：sendError = friendlyMessage()
```

### 6.4 写：点赞（乐观更新）

```text
用户点击点赞
  → TopicDetailViewModel.toggleLike(post)
      ├─ 立即写入 interactions[post.id] = 取反 + 数量 ±1（UI 马上变）
      ├─ repo.toggleLike(postId, currentlyLiked)
      │    ├─ 已赞 → DELETE /post_actions/{postId}?post_action_type_id=2
      │    └─ 未赞 → POST /post_actions  id=…&post_action_type_id=2
      ├─ 成功且服务端 count >= 0 → 用服务端 count 覆盖本地
      └─ 失败 → 回滚到原 liked / count
```

## 7. 线程与协程

| 场景 | 机制 |
|---|---|
| UI 状态更新 | `viewModelScope` 默认 `Dispatchers.Main.immediate` |
| 网络请求 | `suspendCancellableCoroutine` 包裹 OkHttp `enqueue`；协程取消时 `call.cancel()` |
| JSON 解析 | 在调用方协程中执行（默认主线程）；数据量小，未切 IO |
| 本地持久化 | `preferencesDataStore.edit` 本身 suspend；`SharedPreferences` 读写是同步 IO，仅在启动 / 登录态变化时发生 |
| 列表缓存 | `synchronized(cacheLock)` 保护 `LinkedHashMap`（accessOrder = true） |
| 图片加载 | Coil 使用独立 `imageHttpClient`，不共享 API 的 cookie 与连接池 |

## 8. 错误处理模型

### 8.1 异常类型

```kotlin
class HttpException(code: Int, body: String, url: String) : IOException(...)

class ApiException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(...) {
    enum class Kind { NETWORK, AUTH, CSRF, PARSE, RATE_LIMIT, SERVER, UNKNOWN }
    val isCsrfFailure: Boolean get() = kind == Kind.CSRF
}
```

| Kind | 触发条件 | UI 提示（`friendly()` / `friendlyMessage()`） |
|---|---|---|
| `NETWORK` | OkHttp `onFailure`（断网、DNS、TLS） | 网络连接失败，检查网络或试试关闭 DoH |
| `AUTH` | HTTP 401 / 普通 403 | 需要登录后才能查看 / 需要登录 |
| `CSRF` | 403 且响应体含 CSRF 特征，刷新 token 后仍失败 | 走 `else → message` |
| `RATE_LIMIT` | HTTP 429 | 请求太频繁，稍后再试 |
| `SERVER` | HTTP 5xx | 服务器出错了 |
| `PARSE` | 已定义，但当前实现未产生 | — |
| `UNKNOWN` | 其他状态码 / URL 解析失败 | 原始 message 或「未知错误」 |

错误映射位置：

- `DiscourseApi.apiError()`：状态码 → `Kind`；
- `FeedViewModel.friendly()`（文件末尾，internal 扩展）；
- `TopicDetailViewModel.friendlyMessage()`（文件末尾，internal 扩展）。

### 8.2 处理模式

- 读操作：`runCatching { ... }` → `onSuccess` / `onFailure` 更新 state；
- 非关键副作用：`runCatching { ... }` 后静默（`markTopicRead`、`logout` 的服务端删除、通知已读）；
- 登录 / 发帖 / 回复返回 `Result<T>`，由 UI 决定是否弹错；
- 403 CSRF 有且只有一次自动重放，避免死循环（见 06）。

## 9. 关键设计决策

| 决策 | 选择 | 理由 / 代价 |
|---|---|---|
| 依赖注入 | 手写 `AppContainer` | 单例只有 6 个，框架成本大于收益；代价是测试时需手工替换 |
| 导航 | 手写状态 | 页面少、层级浅；代价是无返回栈、无状态恢复、系统返回键不拦截 |
| HTML 渲染 | `TextView` + `HtmlCompat` | 无 WebView 内存 / JS 风险；代价是不支持 img、table、iframe |
| 登录态 | 仅 cookie + CSRF | 标准 browser session，第三方客户端最透明；无设备证明 |
| CSRF | 写请求自动重试一次 | 对调用方透明；用「token 是否变化」防死循环 |
| 列表分页 | `page` | 列表端点本身就是页码分页 |
| 详情分页 | `post_number` | 帖子流稀疏，页码会错位 |
| 列表缓存 | 30 组 LRU，内存 | 返回上一页不重复请求；切站点 / 登出 / 下拉刷新时失效 |
| 点赞 | 乐观更新 | 交互即时；失败回滚；服务端 count 优先 |
| Cookie 序列化 | 自定义 7 字段格式 | `okhttp3.Cookie` 无 serializer；格式简单、可控 |
| 图片客户端 | 独立 OkHttpClient | 不与 API 抢连接，也不携带会话 cookie |
| DoH | 代码保留、默认关闭 | 避免企业网络 / 代理下绕开系统 DNS 导致更慢 |
| 动态取色 | 默认关闭 | Material You 会覆盖社区品牌绿 |
| 通知实时性 | 进入页面刷新 | 省一条 MessageBus 长连接 |

## 10. 站点切换与多站点支持

所有与站点相关的值都通过 provider 动态读取，而不是写死在客户端里：

```kotlin
// AppContainer
DiscourseApi(
    baseUrlProvider = { sessionStore.baseUrl },
    clientProvider = { HttpClients.forSite(sessionStore.baseUrl, sessionStore.cookieJar, sessionStore.csrfTokens) },
    csrfProvider = { sessionStore.csrfTokens.current() },
    csrfPersist = { token -> sessionStore.csrfTokens.update(token) },
)
```

`HttpClients` 按 `baseUrl` 缓存 OkHttpClient，因为 `HeaderInterceptor` 的 `Origin` / `Referer`
是创建时固定的；切换站点后必须调用 `HttpClients.invalidate()` 或依赖新的 `baseUrl`
命中新 key（旧客户端仍留在缓存中）。

**两条切换路径的实际差异**：

| 路径 | 行为 | 是否被 UI 使用 |
|---|---|---|
| `MainViewModel.changeServer(url, onDone)` | 写 `baseUrl` → `clearCookies()` → `container.onSiteChanged()`（清客户端缓存）→ `repository.invalidateLists()` → `boot()` 刷新根状态 | 否（当前无调用方） |
| `ProfileScreen.ServerDialog.onSave` | 写 `baseUrl` → `clearCookies()` → `repository.invalidateLists()`；**不调用 `onSiteChanged()`、不刷新 `MainState`** | 是 |

因此通过「我的 → 服务器地址」切换后：API 请求会因 `baseUrl` 变化而正确指向新站点，
但顶栏标题、站点图标、`LocalSiteBaseUrl` 驱动的头像 / 图片地址要等下次启动 `boot()` 才更新。
这是当前实现的已知不一致，详见 [03-key-flows.md](03-key-flows.md#12-切换站点) 与
[08-ui-layer.md](08-ui-layer.md#10-profile我的)。

## 11. 架构约束与技术债

| 约束 / 债务 | 影响 | 相关位置 |
|---|---|---|
| 无 `NavHost`、无 `BackHandler` | 无返回栈；系统返回键直接退出 Activity | `MainScreen` |
| ViewModel 为 Activity 作用域 | 关闭详情后状态不释放，复开可能看到旧数据 | `viewModel(key = "topic_…")` |
| `MainViewModel.changeServer()` 未接线 | 切站点后根状态不刷新 | `MainViewModel` / `ProfileScreen` |
| `DraftStore` 未接线 | 草稿、阅读进度不生效 | `AppContainer` / `ComposerScreen` |
| `markAllNotificationsRead()` 未调用 | 未读数只能靠 `refreshSession()` 刷新，未真正标记已读 | `UserRepository` / `NotificationsScreen` |
| 通知列表无分页 | 超过一页的通知看不到 | `NotificationsScreen.load()` |
| `toggleLike` 用正则解析 count | 响应结构变化时拿不到准确数量（回退 -1，保留本地推算） | `UserRepository.toggleLike()` |
| `SearchHistoryStore` 用 `Set` 存储 | 注释称「新的在前」，但 `Set` 不保证顺序 | `SearchHistoryStore` |
| `categories()` 响应层级可能不匹配 | 按官方 `/categories.json` 的 `category_list` 包装结构会得到空分类，影响分类标签与发帖分类选择 | `UserRepository.categories()`、`Models.kt` |
| `ApiException.Kind.PARSE` 未使用 | 解析失败会以其他异常形式冒泡 | `DiscourseApi` |
| 无 UI 测试 | 界面回归靠人工 | `app/src/test` |
