# 01 · 项目概览

## 1. 项目定位

LINUX DO Android 是 **LINUX DO 社区（linux.do）的非官方安卓客户端**。
它不依赖官方 App 的私有协议，而是直接调用 Discourse 的标准 REST API：

- 默认站点：`https://linux.do`（编译期常量 `BuildConfig.DEFAULT_BASE_URL`）；
- 运行时可切换为任意自建 Discourse 站点（「我的 → 服务器地址」）；
- 登录态只有 cookie（`_t` 会话）＋ CSRF token，没有 App Attest / mTLS 设备证明。

项目按 iOS 版 `LINUX DO Community 1.0.5` 的功能结构复刻，但只保留第三方客户端能走通的路径。

## 2. 核心能力与实现状态

| 能力 | 状态 | 入口 |
|---|---|---|
| 话题列表（最新 / 热门 / 新帖 / 未读 / 精华 + 周期筛选） | 已实现 | `ui/browse/FeedScreen.kt` |
| 列表下拉刷新、无限滚动、内存缓存 | 已实现 | `FeedViewModel` + `UserRepository.topicList()` |
| 话题详情、楼层分页、楼层定位 | 已实现 | `ui/topic/TopicDetailScreen.kt` |
| 回复（可指定回复楼层） | 已实现 | `TopicDetailViewModel.sendReply()` |
| 点赞 / 取消点赞（乐观更新） | 已实现 | `TopicDetailViewModel.toggleLike()` |
| 收藏 / 取消收藏 | 已实现 | `TopicDetailViewModel.toggleBookmark()` |
| 发新帖（标题 + 分类 + Markdown） | 已实现 | `ui/topic/ComposerScreen.kt` |
| 搜索（300ms 防抖 + 历史记录） | 已实现 | `ui/search/SearchScreen.kt` |
| 通知列表（类型映射中文标签） | 已实现（进入页面时加载） | `ui/notifications/NotificationsScreen.kt` |
| 登录 / 登出（cookie 会话） | 已实现 | `ProfileScreen` + `UserRepository.login()` |
| 个人资料（TL、徽章数、统计） | 已实现 | `ProfileScreen.ProfileCard` |
| 深色模式跟随系统 | 已实现 | `ui/theme/Theme.kt` |
| 话题深链 `/t/{slug}/{id}/{post}` | 已实现 | `MainActivity` + `util/TopicLink.kt` |
| 切换自建 Discourse 站点 | 部分实现（见 §7） | `ProfileScreen.ServerDialog` |
| 编辑已发楼层 | 仅数据层封装，UI 未接 | `UserRepository.editPost()` |
| 上传附件 | 仅 API 封装，UI 未接 | `DiscourseApi.uploadFile()` |
| 草稿保存 / 阅读进度 | 本地存储已实现，UI 未接 | `data/local/DraftStore.kt` |
| 私信、聊天、审核队列 | 未实现 | — |
| 实时未读（MessageBus 长轮询） | 未实现，改为进通知页刷新 | — |
| 图片附件内嵌显示 | 未实现，正文图片被剥离 | `ui/common/HtmlText.kt` |
| onebox / 投票 / 数学公式 | 未实现 | — |

## 3. 技术栈

### 3.1 语言与 UI

| 项 | 版本 / 选择 | 说明 |
|---|---|---|
| Kotlin | 2.0.21 | 协程、`data class`、`sealed` 思维 |
| Jetpack Compose | BOM 2024.10.01 | Material 3，全量 Compose，无 XML 布局 |
| Compose 编译器 | Kotlin 2.0 Compose Plugin | 与 Kotlin 同版本 |
| Activity Compose | 1.9.3 | `ComponentActivity.setContent` |
| Lifecycle | 2.8.7 | `viewModelScope`、`collectAsStateWithLifecycle` 未用，用 `collectAsState` |
| Navigation Compose | 2.8.4 | 已引入依赖，但**未使用 NavHost**（见 02） |
| Material Icons Extended | BOM 管理 | 底部导航、FAB、点赞图标 |

### 3.2 数据与网络

| 项 | 版本 | 用途 |
|---|---|---|
| OkHttp | 4.12.0 | HTTP 客户端、连接池、拦截器 |
| okhttp-dnsoverhttps | 4.12.0 | 备用 DoH 解析器（默认未启用） |
| okhttp-logging-interceptor | 4.12.0 | 仅依赖引入，生产代码未挂载 |
| kotlinx.serialization-json | 1.7.3 | JSON 反序列化 |
| Coil（coil-compose / coil-gif） | 2.7.0 | 头像、缩略图加载 |
| kotlinx-coroutines-android | 1.9.0 | 主线程调度、`viewModelScope` |
| DataStore Preferences | 1.1.1 | 草稿、搜索历史 |
| security-crypto | 1.1.0-alpha06 | `EncryptedSharedPreferences` 存 cookie / CSRF |

### 3.3 构建工具链

| 项 | 版本 |
|---|---|
| Gradle Wrapper | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| JDK | 17（`sourceCompatibility` / `jvmTarget`） |
| compileSdk / targetSdk | 35 / 35 |
| minSdk | 26（Android 8.0） |
| 构建特性 | `compose = true`、`buildConfig = true` |
| 发布构建 | `isMinifyEnabled = true`、`isShrinkResources = true` |

## 4. 应用元信息

| 项 | 值 | 来源 |
|---|---|---|
| applicationId | `org.linuxdo.android` | [app/build.gradle.kts](../../app/build.gradle.kts) |
| versionName / versionCode | `1.0.0` / `1` | 同上 |
| 默认站点 | `https://linux.do` | `buildConfigField("String", "DEFAULT_BASE_URL", …)` |
| Application 类 | `.LinuxDoApp` | [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml) |
| 入口 Activity | `.MainActivity`（`singleTop`、edge-to-edge） | 同上 |
| 网络权限 | `INTERNET`、`ACCESS_NETWORK_STATE`、`POST_NOTIFICATIONS` | 同上 |
| 明文流量 | 禁止（`usesCleartextTraffic="false"`） | 同上，故只支持 https 站点 |
| 深链 | `https://linux.do/t/` 前缀 | Manifest `VIEW` intent-filter |
| FileProvider | `${applicationId}.fileprovider` | 已声明，当前代码未使用 |

## 5. 架构风格

| 维度 | 选择 |
|---|---|
| 界面 | 单 Activity + 全 Compose；无 Fragment、无 XML 布局 |
| 路由 | 手写状态切换（tab / `openTopicId` / `composing`），未接 `NavHost` |
| 状态 | `StateFlow<XxxState>` + 不可变 `data class` + `copy()` |
| 业务层 | 单一 `UserRepository`，负责端点编排、模型转换、缓存、登录态维护 |
| 网络层 | `DiscourseApi` 统一封装，Repository 不直接碰 OkHttp |
| 依赖注入 | 无框架；`AppContainer` 在 `LinuxDoApp` 中创建并持有 |
| 本地存储 | 站点 / 登录信息用 SharedPreferences（cookie 加密）；草稿 / 历史用 DataStore |
| 鉴权 | cookie + CSRF token；无 token 刷新中间件，重试逻辑内置在 `DiscourseApi` |
| 错误模型 | 统一 `ApiException(kind)`，UI 层映射中文提示 |

## 6. 仓库目录地图

```text
.
├── app/                                  Android 应用模块（唯一业务模块）
│   ├── build.gradle.kts                  模块配置、依赖、签名
│   ├── proguard-rules.pro                release 混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       权限、Activity、深链、FileProvider
│       │   ├── java/org/linuxdo/android/ Kotlin 源码（34 个文件）
│       │   └── res/                      主题、字符串、图标、file_paths
│       └── test/java/org/linuxdo/android/ JVM 单元测试（4 个文件）
├── docs/                                 既有设计与 API 文档
│   └── wiki/                             本 Code Wiki
├── gradle/wrapper/                       Gradle 8.9 wrapper
├── build.gradle.kts                      根构建脚本（插件版本声明）
├── settings.gradle.kts                   仓库与模块声明
├── gradle.properties                     Gradle / AndroidX / Kotlin 全局开关
├── keystore.properties.example           本地签名配置模板
├── gradlew / gradlew.bat                 Gradle 启动脚本
├── .github/workflows/build.yml           CI：单测、打包、签名校验
├── README.md                             项目说明（功能、架构、取舍）
└── LICENSE                               MIT
```

## 7. 支持范围与已知限制

这些限制来自代码本身，修改相关功能前应先了解：

### 7.1 渲染与内容

- **正文图片不显示**：`HtmlContent` 会剥掉 `<img>`，避免 TextView 出现空白；列表缩略图仍可显示。
- `<video>` 显示为 `[视频]`，`<iframe>` 显示为 `[嵌入内容]`。
- onebox 链接卡片、投票、数学公式不渲染；表格样式丢失。
- 搜索结果高亮被 `Html.toPlain()` 去标签后丢失。

### 7.2 功能空缺

- 无编辑楼层 UI（`editPost` 已实现但未调用）。
- 无附件上传 UI（`uploadFile` 已实现但未调用）。
- 无草稿自动保存（`DraftStore` 已实现但未注入任何 Screen）。
- 无阅读进度上报 UI（`TopicDetailViewModel.markRead()` 与 `markTopicRead` 均未被 UI 调用）。
- 通知不分页：只拉第一页 `notifications.json`，未使用 `load_more_notifications`。
- 通知不做「全部已读」：`markAllNotificationsRead()` 已实现但未调用。
- 分类接口存在响应层级不一致的风险（见 [05-data-layer.md](05-data-layer.md#35-方法话题列表)）；
  若按官方响应结构返回 `category_list`，分类列表会为空，发帖页将无法选择分类。
- 登录不支持 2FA：请求固定 `second_factor_method=1`，未实现 `POST /session/2fa`。
- 无私信、聊天、审核队列、用户主页话题列表 UI（`userTopics` 已封装但未用）。

### 7.3 站点与网络

- 只支持 https：Manifest 禁止明文流量；服务器地址输入框虽然接受 `http://` 前缀，但实际请求会被系统拦截。
- DoH 已实现但**默认未挂载**；DNS 污染场景需要手动在 `HttpClients.base` 加 `.dns(Doh.dns())`。
- 换站点的两条路径行为不一致：`MainViewModel.changeServer()` 会刷新根状态和客户端缓存，但 UI 实际走的是 `ProfileScreen.ServerDialog` 的简化路径，**不会立即刷新标题 / 图标 / 站点相关图片地址**，详见 03 与 08。

### 7.4 平台行为

- 未使用 `NavHost` 与 `BackHandler`：详情页 / 发帖页的返回依赖顶栏按钮；系统返回键不会先关闭子页面。
- ViewModel 通过 `viewModel()` 直接取得，作用域是 Activity 的 `ViewModelStore`；关闭详情页后 VM 不会立即销毁，复开同一话题可能复用旧状态。
- 无 UI 自动化测试；Compose 层未覆盖。

## 8. 与 iOS 版的刻意差异

README 与 `docs/REVERSE-ENGINEERING.md` 记录了逆向结论，代码上体现为：

| iOS 能力 | 安卓实现 | 原因 |
|---|---|---|
| App Attest / DeviceCheck / mTLS | 不做 | 绑定 Apple 生态；官方 App 专属通道，第三方走标准 cookie 更透明 |
| Pajero 微应用（JSC 运行时、190+ 类） | 不移植 | 服务端商店默认关闭（`catalogURL=null`），移植价值低 |
| MessageBus 长轮询 | 进通知页时刷新 | 省一条常驻连接；需要时可加 `GET /message-bus/{clientId}/poll` |
| 原生 HTML 解析排版 | `TextView` + `HtmlCompat` | 成本最低的等价方案；无 JS 执行面、省内存 |

## 9. 与既有文档的分工

| 文档 | 关注点 |
|---|---|
| `README.md` | 项目是什么、功能对照、构建入口、设计取舍摘要 |
| `docs/ARCHITECTURE.md` | 分层、数据流、网络、HTML 渲染、状态管理的设计说明 |
| `docs/API.md` | Discourse 端点、参数、真实响应示例、限流 |
| `docs/ROADMAP.md` | 后续优先级与技术债 |
| `docs/REVERSE-ENGINEERING.md` | iOS 样本逆向结论与取舍依据 |
| `docs/CONTRIBUTING.md` | 编码规范、提交规范、调试与测试约定 |
| **本 Wiki** | 逐模块、逐类、逐方法的代码结构索引与调用关系 |
