# 04 · 模块职责与依赖

## 1. 包结构与职责

源码根：`app/src/main/java/org/linuxdo/android/`

| 包 | 职责 | 关键类型 | 不应出现 |
|---|---|---|---|
| `org.linuxdo.android` | 进程入口、依赖装配、单 Activity | `LinuxDoApp`、`AppContainer`、`MainActivity` | 业务逻辑、网络细节 |
| `data` | 业务编排与登录态 | `UserRepository`、`SessionStore` | Compose、OkHttp 直接调用 |
| `data.model` | 序列化模型与只读派生属性 | `Topic`、`Post`、`TopicDetail`、`Notification` | Android、网络 IO |
| `data.local` | DataStore 本地存储 | `DraftStore`、`SearchHistoryStore` | 网络访问 |
| `data.remote` | HTTP、请求头、Cookie、CSRF、DoH | `DiscourseApi`、`HttpClients`、`HeaderInterceptor`、`PersistentCookieJar`、`CsrfTokens`、`Doh` | 业务语义（「已收藏」等） |
| `ui` | 根界面与根状态 | `MainScreen`、`MainViewModel`、`MainState`、`simpleFactory` | 直接读写加密存储 |
| `ui.browse` | 话题列表 | `FeedScreen`、`FeedViewModel`、`FeedState`、`ComposeShims` | 详情逻辑 |
| `ui.topic` | 详情、回复、发帖 | `TopicDetailScreen`、`TopicDetailViewModel`、`ComposerScreen` | 列表筛选 |
| `ui.search` | 搜索 | `SearchScreen`、`SearchViewModel`、`SearchState` | 其他页面逻辑 |
| `ui.notifications` | 通知 | `NotificationsScreen`、`typeLabel()` | 全局通知服务 |
| `ui.profile` | 我的 / 登录 / 服务器设置 | `ProfileScreen` | 业务缓存 |
| `ui.common` | 跨功能 Compose 与站点上下文 | `HtmlContent`、`CollapsibleHtml`、`LocalSiteBaseUrl`、`avatarUrlOf` | 具体业务 |
| `ui.theme` | 配色与基础组件 | `LinuxDoTheme`、`AvatarFallback`、`CategoryChip`、`parseHexColor` | 页面布局 |
| `util` | 纯函数工具 | `Html`、`TimeFmt`、`TopicLink`、`shortCount()` | Android / 网络依赖 |

## 2. 文件索引

行数为分析基线时的统计，用于判断文件规模，不是维护目标。

### 2.1 根包（3 个文件，167 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [LinuxDoApp.kt](../../app/src/main/java/org/linuxdo/android/LinuxDoApp.kt) | 41 | `Application`；创建 `AppContainer`；配置 Coil `ImageLoader`（内存 25%、磁盘 256MB、crossfade） | `LinuxDoApp.container` |
| [AppContainer.kt](../../app/src/main/java/org/linuxdo/android/AppContainer.kt) | 50 | 手写依赖容器；装配 SessionStore、图片客户端、API、仓库、两个本地 Store | `sessionStore`、`imageHttpClient`、`api`、`repository`、`draftStore`、`searchHistoryStore`、`onSiteChanged()` |
| [MainActivity.kt](../../app/src/main/java/org/linuxdo/android/MainActivity.kt) | 76 | 单 Activity；edge-to-edge；深链接收（`onCreate` / `onNewIntent`）；注入 `LocalSiteBaseUrl` | `deepLinkTopicId`、`deepLinkPostNumber` |

### 2.2 data（2 个文件，449 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [SessionStore.kt](../../app/src/main/java/org/linuxdo/android/data/SessionStore.kt) | 128 | 站点地址、用户信息、站点标题/图标（普通 prefs）；cookie / CSRF（加密 prefs） | `baseUrl`、`username`、`userId`、`isLoggedIn`、`cookieJar`、`csrfTokens`、`clearCookies()` |
| [UserRepository.kt](../../app/src/main/java/org/linuxdo/android/data/UserRepository.kt) | 321 | 所有 Discourse 业务端点；列表 LRU 缓存；登录态更新 | `ListFilter`、`Period`、会话/列表/详情/写操作/通知/搜索/用户方法 |

### 2.3 data.model（2 个文件，578 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [Models.kt](../../app/src/main/java/org/linuxdo/android/data/model/Models.kt) | 514 | 全部 `@Serializable` 请求/响应模型；派生属性 | `SessionResponse`、`User`、`Topic`、`TopicDetail`、`Post`、`Notification`、`SearchResponse` 等 |
| [TagOrString.kt](../../app/src/main/java/org/linuxdo/android/data/model/TagOrString.kt) | 64 | 兼容 tags 的「对象数组 / 字符串数组」两种形态 | `TagOrString`、`TagOrStringSerializer` |

### 2.4 data.local（2 个文件，103 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [DraftStore.kt](../../app/src/main/java/org/linuxdo/android/data/local/DraftStore.kt) | 70 | 草稿、阅读位置、站点信息缓存（DataStore `linuxdo.drafts`） | `save/load/delete/allKeys`、`saveLastRead/lastRead`、`cacheSiteBlob/siteBlob` |
| [SearchHistoryStore.kt](../../app/src/main/java/org/linuxdo/android/data/local/SearchHistoryStore.kt) | 33 | 搜索历史（DataStore `linuxdo.search`，最多 20 条） | `add/list/clear` |

### 2.5 data.remote（6 个文件，436 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [DiscourseApi.kt](../../app/src/main/java/org/linuxdo/android/data/remote/DiscourseApi.kt) | 207 | URL 拼接、GET/POST/PUT/DELETE/上传、CSRF 重试、错误分类、协程桥接 | `DiscourseApi`、`ApiException`、`HttpException` |
| [HeaderInterceptor.kt](../../app/src/main/java/org/linuxdo/android/data/remote/HeaderInterceptor.kt) | 61 | 补齐 Discourse 必需请求头；写请求补 Origin/Referer；捕获响应 CSRF | `HeaderInterceptor` |
| [HttpClients.kt](../../app/src/main/java/org/linuxdo/android/data/remote/HttpClients.kt) | 39 | 基础客户端配置；按 baseUrl 缓存客户端；切换站点时清缓存 | `HttpClients.forSite()`、`invalidate()` |
| [PersistentCookieJar.kt](../../app/src/main/java/org/linuxdo/android/data/remote/PersistentCookieJar.kt) | 54 | 内存 + 落盘的 CookieJar；过期即删除 | `saveFromResponse`、`loadForRequest`、`clear`、`snapshot` |
| [CsrfTokens.kt](../../app/src/main/java/org/linuxdo/android/data/remote/CsrfTokens.kt) | 30 | CSRF token 的 volatile 内存缓存 + 读写回调 | `current()`、`update()`、`clear()`、`hasToken` |
| [Doh.kt](../../app/src/main/java/org/linuxdo/android/data/remote/Doh.kt) | 45 | 阿里 / Google 双路 DoH 解析器（默认未挂载） | `Doh.resolver`、`Doh.dns()` |

### 2.6 ui（2 个文件，306 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [MainScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/MainScreen.kt) | 198 | 底部导航、FAB、整屏覆盖详情/发帖、深链消费 | `MainScreen`、`simpleFactory` |
| [MainViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/MainViewModel.kt) | 108 | 根状态：启动、站点信息、用户、未读角标、登出、切站点 | `MainState`、`MainViewModel`、`MainViewModelFactory` |

### 2.7 ui.browse（3 个文件，533 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [FeedScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedScreen.kt) | 354 | 筛选栏、下拉刷新、无限滚动、话题行、共享加载/错误/空态 | `FeedScreen`、`CenterLoading`、`ErrorBox`、`EmptyBox` |
| [FeedViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedViewModel.kt) | 161 | 列表状态、筛选、分页、分类缓存、错误提示 | `FeedState`、`FeedViewModel`、`friendly()` |
| [ComposeShims.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/ComposeShims.kt) | 18 | `StateFlow` → Compose `State`、`snapshotFlow` 简写 | `collectState()`、`snapshotOf()` |

### 2.8 ui.topic（3 个文件，928 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [TopicDetailScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailScreen.kt) | 511 | 详情顶栏、楼层列表、楼层定位、回复输入、点赞/回复交互 | `TopicDetailScreen` |
| [TopicDetailViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailViewModel.kt) | 218 | 详情加载、按 post_number 分页、回复、点赞乐观更新、收藏、已读 | `TopicState`、`PostInteraction`、`TopicDetailViewModel`、`friendlyMessage()` |
| [ComposerScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/ComposerScreen.kt) | 199 | 发新帖：标题、顶级分类选择、Markdown 正文、校验与提交 | `ComposerScreen` |

### 2.9 ui.search（2 个文件，322 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [SearchScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchScreen.kt) | 226 | 搜索框、历史标签、结果列表、加载/空/错误态 | `SearchScreen` |
| [SearchViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchViewModel.kt) | 96 | 防抖搜索、历史读写、结果与话题索引 | `SearchState`、`SearchViewModel` |

### 2.10 ui.notifications（1 个文件，224 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [NotificationsScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/notifications/NotificationsScreen.kt) | 224 | 通知加载、类型标签映射、行渲染、错误/空态 | `NotificationsScreen`、`typeLabel()`（private） |

### 2.11 ui.profile（1 个文件，402 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [ProfileScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/profile/ProfileScreen.kt) | 402 | 登录表单、资料卡、设置项、服务器地址弹窗 | `ProfileScreen` |

### 2.12 ui.common（2 个文件，127 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [HtmlText.kt](../../app/src/main/java/org/linuxdo/android/ui/common/HtmlText.kt) | 96 | cooked HTML → TextView；剥 img/video/iframe；折叠长文 | `HtmlContent`、`CollapsibleHtml` |
| [SiteUrl.kt](../../app/src/main/java/org/linuxdo/android/ui/common/SiteUrl.kt) | 31 | 当前站点地址的 Compose CompositionLocal 与地址补全 | `LocalSiteBaseUrl`、`siteBaseUrl()`、`absoluteImageUrl()`、`avatarUrlOf()` |

### 2.13 ui.theme（2 个文件，139 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [Theme.kt](../../app/src/main/java/org/linuxdo/android/ui/theme/Theme.kt) | 57 | 亮/暗配色；默认关闭动态取色 | `LinuxDoTheme` |
| [Components.kt](../../app/src/main/java/org/linuxdo/android/ui/theme/Components.kt) | 82 | 头像兜底、分类标签、hex 颜色解析、前景色计算 | `AvatarFallback`、`CategoryChip`、`parseHexColor`、`onColorFor`、`defaultCategoryColor` |

### 2.14 util（3 个文件，314 行）

| 文件 | 行数 | 职责 | 对外输出 |
|---|---|---|---|
| [Html.kt](../../app/src/main/java/org/linuxdo/android/util/Html.kt) | 149 | HTML 转纯文本、安全解实体、抽首图、相对地址补全、头像 URL | `Html.toPlain/unescape/firstImageSrc/isCjkDominant`、`avatarUrl()`、`absolutize()` |
| [TimeFmt.kt](../../app/src/main/java/org/linuxdo/android/util/TimeFmt.kt) | 56 | ISO8601 → 相对时间 / 绝对时间 / 日期；数字缩写 | `TimeFmt.relative/absolute/dateOnly`、`Int.shortCount()` |
| [TopicLink.kt](../../app/src/main/java/org/linuxdo/android/util/TopicLink.kt) | 109 | 纯字符串解析 Discourse 话题链接 | `TopicLink.Target`、`TopicLink.parse()`、`TopicLink.parsePath()` |

## 3. 包依赖矩阵

`●` = 直接 import；`○` = 仅通过同包内部传递；空 = 无依赖。

| 从 \ 到 | `util` | `data.model` | `data.remote` | `data.local` | `data`（仓库/Store） | `ui.common` | `ui.theme` | 其他 `ui.*` |
|---|---|---|---|---|---|---|---|---|
| 根包 | ● | ● | ● | ● | ● | ● | ● | ● |
| `data` |  | ● | ● |  |  |  |  |  |
| `data.model` |  |  |  |  |  |  |  |  |
| `data.local` |  |  |  |  |  |  |  |  |
| `data.remote` |  |  |  |  |  |  |  |  |
| `ui`（根） | ● | ● |  |  | ● | ● | ● | ● |
| `ui.browse` | ● | ● | ●（`ApiException`） |  | ● | ● | ● |  |
| `ui.topic` | ● | ● | ●（`ApiException`） |  | ● | ● | ● | ●（`ui.browse` 共享组件、`ui` 根） |
| `ui.search` | ● | ● |  | ● | ● |  |  | ●（`ui.browse.collectState`、`ui` 根） |
| `ui.notifications` | ● | ● |  |  | ●（`AppContainer`） |  |  |  |
| `ui.profile` | ● | ● |  |  | ●（`AppContainer`） | ● | ● |  |
| `ui.common` | ● |  |  |  |  |  |  |  |
| `ui.theme` |  |  |  |  |  |  |  |  |
| `util` |  |  |  |  |  |  |  |  |

**必须保持的规则**：

1. `data.model` 不依赖任何项目内其他包（只依赖 kotlinx.serialization）；
2. `data.remote` 不知道模型与业务，只收发字符串；
3. `util` 不依赖 Android（`TopicLink` 明确不用 `android.net.Uri`，因此可 JVM 测试）；
4. UI 不直接 `OkHttpClient.newCall()`，必须经过 `UserRepository → DiscourseApi`。

## 4. 对象生命周期

| 对象 | 创建位置 | 生命周期 | 备注 |
|---|---|---|---|
| `AppContainer` | `LinuxDoApp.onCreate` | 进程级 | `lateinit var`，`private set` |
| `SessionStore` | `AppContainer` | 进程级 | 普通 prefs 立即创建；`secure` / `cookieJar` / `csrfTokens` 为 `lazy` |
| `imageHttpClient` | `AppContainer` | 进程级 | Coil 通过 `ImageLoaderFactory` 使用 |
| `DiscourseApi` | `AppContainer` | 进程级 | 内部无状态，除注入的 lambda |
| `UserRepository` | `AppContainer` | 进程级 | 持有 30 组 LRU 列表缓存 |
| `OkHttpClient`（API） | `HttpClients.forSite` | 按 baseUrl 缓存 | 切换站点时 `invalidate()` 清空 |
| `DraftStore` / `SearchHistoryStore` | `AppContainer` | 进程级 | DataStore 委托为文件级 |
| `MainViewModel` | `viewModel(factory)` in `MainActivity` | Activity | 配置变更时存活 |
| `FeedViewModel` | `viewModel(key="feed")` in `MainScreen` | Activity | 切 Tab 不丢 |
| `SearchViewModel` | `viewModel(key="search")` in `SearchScreen` | Activity | 离开搜索页不丢 |
| `TopicDetailViewModel` | `viewModel(key="topic_{id}_{post}")` | Activity | 关闭详情后不立即销毁，复开同 key 复用 |
| 通知 / 资料 / 发帖状态 | 各自 Composable 的 `remember` | 组合 | 离开页面即丢弃 |

## 5. 扩展点（改代码时从这里入手）

| 需求 | 改动位置 |
|---|---|
| 新增一个只读端点 | `UserRepository` 加 suspend 方法 → 需要时加模型 → ViewModel 调用 |
| 新增一个写端点 | `DiscourseApi.postForm/putForm/deleteForm` → `UserRepository` 封装为 `Result` |
| 新增页面 | 新建 `ui/<feature>/XxxScreen.kt` + `XxxViewModel.kt` → 在 `MainScreen` 接线 |
| 新增模型字段 | `Models.kt` 加带默认值的字段 + `@SerialName`（不要删已有字段） |
| 新增请求头 | `HeaderInterceptor.intercept()`（注意不要覆盖调用方显式设置的头） |
| 新增站点维度能力 | 从 `SessionStore.baseUrl` 读取，不要写死 `linux.do` |
| 启用 DoH | `HttpClients.base` 构建链加 `.dns(Doh.dns())` |
| 接入草稿 | `ComposerScreen` / `TopicDetailScreen` 注入 `container.draftStore` |
| 加单元测试 | `app/src/test/java/...`；纯逻辑优先，网络用 MockWebServer |
