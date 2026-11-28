# LINUX DO Android · Code Wiki

面向开发者的代码手册。目标是把三件事讲清楚：

1. 项目由哪些模块组成、各自负责什么；
2. 关键类与函数在哪里、签名和语义是什么；
3. 模块之间、与第三方库之间如何依赖，以及如何构建运行。

> 分析基线：commit `19d5c88`，应用版本 `1.0.0`，包名 `org.linuxdo.android`。
> 行号会随迭代漂移，本文档以「类名 / 方法名 / 文件路径」为锚点。

---

## 文档目录

| 编号 | 文档 | 回答的问题 |
|---|---|---|
| 00 | 本页 | 从哪开始读、关键类在哪 |
| 01 | [项目概览](01-overview.md) | 这是什么项目、用什么技术、做到什么程度 |
| 02 | [整体架构](02-architecture.md) | 分层、启动、导航、状态、数据流、设计取舍 |
| 03 | [关键流程](03-key-flows.md) | 登录、列表、详情、点赞、发帖、深链如何跑通 |
| 04 | [模块职责与依赖](04-module-reference.md) | 每个包 / 文件干什么、依赖方向 |
| 05 | [数据层参考](05-data-layer.md) | `SessionStore`、`UserRepository`、本地存储的类与方法 |
| 06 | [网络层参考](06-network-layer.md) | `DiscourseApi`、拦截器、Cookie、CSRF、DoH、异常 |
| 07 | [数据模型参考](07-model-reference.md) | 所有 `@Serializable` 模型与字段含义 |
| 08 | [UI 层参考](08-ui-layer.md) | Activity、Screen、ViewModel、主题、共享组件 |
| 09 | [工具函数参考](09-utilities.md) | HTML、时间、深链解析 |
| 10 | [依赖清单](10-dependencies.md) | 内部依赖图 + Gradle 第三方库 |
| 11 | [构建与运行](11-build-and-run.md) | 环境、命令、签名、CI、排障 |
| 12 | [测试](12-testing.md) | 测试用例、运行方式、覆盖缺口 |

---

## 10 秒速览

LINUX DO Android 是一个**非官方 Discourse 安卓客户端**：使用 Discourse 的 REST API，
以 Kotlin + Jetpack Compose 复刻 iOS 版 `LINUX DO Community 1.0.5` 的功能结构。
默认站点 `https://linux.do`，可在「我的 → 服务器地址」切换为任意自建 Discourse。

| 指标 | 值 |
|---|---|
| 包名 / applicationId | `org.linuxdo.android` |
| 版本 | `1.0.0`（versionCode 1） |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| 语言 / UI | Kotlin 2.0.21 / Jetpack Compose（Material 3） |
| 架构 | 单 Activity · MVVM · Repository · 手写依赖容器 |
| 网络 / JSON | OkHttp 4.12 + kotlinx.serialization 1.7.3 |
| 主代码规模 | 34 个 `.kt`，约 5,028 行 |
| 测试规模 | 4 个 JVM 单测文件，约 365 行 |
| 构建工具链 | Gradle 8.9 + AGP 8.7.3 + JDK 17 |

---

## 关键类速查

| 我想了解… | 关键类型 | 文件 |
|---|---|---|
| 应用入口 / 依赖装配 | `LinuxDoApp`、`AppContainer` | [LinuxDoApp.kt](../../app/src/main/java/org/linuxdo/android/LinuxDoApp.kt)、[AppContainer.kt](../../app/src/main/java/org/linuxdo/android/AppContainer.kt) |
| 单 Activity 与深链入口 | `MainActivity` | [MainActivity.kt](../../app/src/main/java/org/linuxdo/android/MainActivity.kt) |
| 根界面 / 底部导航 | `MainScreen`、`MainViewModel`、`MainState` | [MainScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/MainScreen.kt)、[MainViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/MainViewModel.kt) |
| 话题列表 | `FeedScreen`、`FeedViewModel`、`FeedState` | [FeedScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedScreen.kt)、[FeedViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/browse/FeedViewModel.kt) |
| 话题详情 / 楼层分页 / 点赞 | `TopicDetailScreen`、`TopicDetailViewModel`、`TopicState` | [TopicDetailScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailScreen.kt)、[TopicDetailViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/TopicDetailViewModel.kt) |
| 发新帖 | `ComposerScreen` | [ComposerScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/topic/ComposerScreen.kt) |
| 搜索 | `SearchScreen`、`SearchViewModel` | [SearchScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchScreen.kt)、[SearchViewModel.kt](../../app/src/main/java/org/linuxdo/android/ui/search/SearchViewModel.kt) |
| 通知 | `NotificationsScreen`、`typeLabel()` | [NotificationsScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/notifications/NotificationsScreen.kt) |
| 我的 / 登录 / 切换站点 | `ProfileScreen` | [ProfileScreen.kt](../../app/src/main/java/org/linuxdo/android/ui/profile/ProfileScreen.kt) |
| 业务 API 总入口 | `UserRepository` | [UserRepository.kt](../../app/src/main/java/org/linuxdo/android/data/UserRepository.kt) |
| 登录态、站点地址、Cookie | `SessionStore` | [SessionStore.kt](../../app/src/main/java/org/linuxdo/android/data/SessionStore.kt) |
| HTTP 封装 / CSRF 重试 | `DiscourseApi`、`ApiException` | [DiscourseApi.kt](../../app/src/main/java/org/linuxdo/android/data/remote/DiscourseApi.kt) |
| OkHttp 客户端 / 请求头 | `HttpClients`、`HeaderInterceptor` | [HttpClients.kt](../../app/src/main/java/org/linuxdo/android/data/remote/HttpClients.kt)、[HeaderInterceptor.kt](../../app/src/main/java/org/linuxdo/android/data/remote/HeaderInterceptor.kt) |
| Cookie 持久化 | `PersistentCookieJar` | [PersistentCookieJar.kt](../../app/src/main/java/org/linuxdo/android/data/remote/PersistentCookieJar.kt) |
| CSRF token 缓存 | `CsrfTokens` | [CsrfTokens.kt](../../app/src/main/java/org/linuxdo/android/data/remote/CsrfTokens.kt) |
| 草稿 / 搜索历史 | `DraftStore`、`SearchHistoryStore` | [DraftStore.kt](../../app/src/main/java/org/linuxdo/android/data/local/DraftStore.kt)、[SearchHistoryStore.kt](../../app/src/main/java/org/linuxdo/android/data/local/SearchHistoryStore.kt) |
| 数据模型 | `Topic`、`Post`、`TopicDetail`、`Notification` 等 | [Models.kt](../../app/src/main/java/org/linuxdo/android/data/model/Models.kt) |
| HTML 渲染 / 纯文本提取 | `HtmlContent`、`Html` | [HtmlText.kt](../../app/src/main/java/org/linuxdo/android/ui/common/HtmlText.kt)、[Html.kt](../../app/src/main/java/org/linuxdo/android/util/Html.kt) |
| 时间 / 数字格式化 | `TimeFmt`、`shortCount()` | [TimeFmt.kt](../../app/src/main/java/org/linuxdo/android/util/TimeFmt.kt) |
| 深链解析 | `TopicLink` | [TopicLink.kt](../../app/src/main/java/org/linuxdo/android/util/TopicLink.kt) |

---

## 推荐阅读路径

| 目标 | 顺序 |
|---|---|
| 第一次接触项目 | 01 → 02 → 03 → 04 |
| 改列表 / 详情 / 搜索等 UI | 08 → 03 → 05（需要数据时） |
| 改网络、登录、CSRF | 06 → 05 → 03 |
| 排查接口返回或字段缺失 | 06 → 07 → 12 |
| 打包发布 / 配 CI | 11 → 10 |
| 做代码评审 | 02（设计取舍）→ 04（依赖规则）→ 12（测试缺口） |

---

## 术语表

| 术语 | 含义 |
|---|---|
| Discourse | 开源论坛软件，本项目的后端；提供 REST API |
| `cooked` | 服务端渲染好的 HTML 正文（详情 / 摘要主用） |
| `raw` | 用户输入的原始 Markdown / BBCode（编辑时用） |
| topic / post | 话题 / 楼层 |
| `post_number` | 楼层号，话题内从 1 开始；与 `post.id` 不同 |
| `stream` | 话题详情的 `post_stream.stream`，包含话题全部 post id |
| CSRF token | Discourse 对非 GET 请求校验的 `X-CSRF-Token` |
| `_t` cookie | Discourse 的会话 cookie（HttpOnly） |
| TL | Trust Level，Discourse 用户信任等级 |
| onebox | Discourse 的链接预览卡片 |
| DoH | DNS over HTTPS |
| PM | Private Message，私信 |
| FAB | Floating Action Button，悬浮操作按钮 |
| `avatar_template` | 含 `{size}` 占位符的头像路径模板 |

---

## 与现有 docs 的关系

仓库里已有一组设计文档：`ARCHITECTURE.md` 讲「为什么这样设计」，`API.md` 是端点参考，
`ROADMAP.md` 记录后续计划。本 Wiki 是**代码结构手册**：它覆盖全部源码文件、类与方法签名、
实际调用关系，并标注「已实现但未接入」的预留能力，适合作为阅读代码时的索引。

维护建议：改动类名、方法签名或包结构时同步更新对应页面；只改业务细节时可不动 Wiki。
