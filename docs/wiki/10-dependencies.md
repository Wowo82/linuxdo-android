# 10 · 依赖清单

本文回答两类问题：**代码之间怎么依赖**、**构建时引入哪些第三方库**。

---

## 1. 模块结构

```text
LinuxDo（rootProject）
└── :app（com.android.application，唯一业务模块）
```

| 构建文件 | 作用 |
|---|---|
| [settings.gradle.kts](../../settings.gradle.kts) | 声明 rootProject 名与 `:app`；配置插件仓库与依赖仓库 |
| [build.gradle.kts](../../build.gradle.kts)（根） | 仅声明插件版本，`apply false` |
| [app/build.gradle.kts](../../app/build.gradle.kts) | Android 配置、签名、构建类型、全部依赖 |
| [gradle.properties](../../gradle.properties) | Gradle JVM、并行、缓存、AndroidX、Kotlin 代码风格 |
| [gradle/wrapper/gradle-wrapper.properties](../../gradle/wrapper/gradle-wrapper.properties) | Gradle 8.9 分发地址 |

仓库配置：

- `pluginManagement`：`google()`（带 group 过滤）、`mavenCentral()`、`gradlePluginPortal()`；
- `dependencyResolutionManagement`：`FAIL_ON_PROJECT_REPOS`，即模块内不能再声明仓库；
- 依赖仓库：`google()`、`mavenCentral()`。

---

## 2. 内部依赖图

```text
LinuxDoApp / AppContainer / MainActivity
   │
   ├──► ui（MainScreen / MainViewModel）
   │       ├──► ui.browse ──► data.UserRepository、data.model、util、ui.theme、ui.common
   │       ├──► ui.topic  ──► data.UserRepository、data.model、data.remote.ApiException、util
   │       ├──► ui.search ──► data.UserRepository、data.local.SearchHistoryStore
   │       ├──► ui.notifications ──► data.model、util、AppContainer
   │       └──► ui.profile ──► data.model、util、ui.theme、ui.common
   │
   ├──► data.UserRepository ──► data.remote.DiscourseApi、data.model
   ├──► data.SessionStore ──► data.remote.PersistentCookieJar、CsrfTokens
   ├──► data.local.DraftStore / SearchHistoryStore（无项目内依赖）
   └──► data.remote（无项目内依赖）
```

严格规则：

1. `data.model` 只依赖 `kotlinx.serialization`；
2. `data.remote` 只依赖 OkHttp / 协程，不 import 模型与业务；
3. `util` 只依赖 JDK（`java.time`、`java.net` 等），不依赖 Android；
4. UI 不直接访问 OkHttp / SharedPreferences。

完整矩阵见 [04-module-reference.md](04-module-reference.md#3-包依赖矩阵)。

---

## 3. Gradle 插件

| 插件 | 版本 | 用途 |
|---|---|---|
| `com.android.application` | 8.7.3 | Android 应用构建 |
| `org.jetbrains.kotlin.android` | 2.0.21 | Kotlin 编译 |
| `org.jetbrains.kotlin.plugin.compose` | 2.0.21 | Compose 编译器（Kotlin 2.0 起独立） |
| `org.jetbrains.kotlin.plugin.serialization` | 2.0.21 | `@Serializable` 代码生成 |

---

## 4. 运行时依赖

### 4.1 AndroidX / Compose

| 依赖 | 版本 | 用途 | 使用位置 |
|---|---|---|---|
| `androidx.compose:compose-bom` | 2024.10.01 | 统一 Compose 版本 | 全局 |
| `androidx.core:core-ktx` | 1.15.0 | Kotlin 扩展 | 全局 |
| `androidx.activity:activity-compose` | 1.9.3 | `setContent`、`enableEdgeToEdge` | `MainActivity` |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | 生命周期 | 全局 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | `viewModel()` | 所有 ViewModel 获取 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | Compose 生命周期集成 | 依赖存在，`collectAsStateWithLifecycle` 未使用 |
| `androidx.compose.ui:ui` | BOM | 基础 UI | 全局 |
| `androidx.compose.ui:ui-tooling-preview` | BOM | `@Preview` 支持 | 依赖存在，代码中无 `@Preview` |
| `androidx.compose.material3:material3` | BOM | Material 3 组件 | 全部界面 |
| `androidx.compose.material:material-icons-extended` | BOM | 扩展图标 | 底部导航、FAB、点赞、收藏 |
| `androidx.compose.foundation:foundation` | BOM | 布局、LazyColumn、clickable | 全部界面 |
| `androidx.navigation:navigation-compose` | 2.8.4 | 导航 | **依赖存在，但未使用 `NavHost`** |
| `androidx.datastore:datastore-preferences` | 1.1.1 | 草稿、搜索历史 | `DraftStore`、`SearchHistoryStore` |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | `EncryptedSharedPreferences` | `SessionStore` |

### 4.2 网络 / JSON / 图片

| 依赖 | 版本 | 用途 | 使用位置 |
|---|---|---|---|
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP 客户端 | `HttpClients`、`DiscourseApi`、图片客户端 |
| `com.squareup.okhttp3:okhttp-dnsoverhttps` | 4.12.0 | DoH 解析 | `Doh`（默认未挂载） |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.0 | 请求日志 | **已引入但未挂载**（调试用） |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | JSON | `DiscourseApi.json`、所有模型 |
| `io.coil-kt:coil-compose` | 2.7.0 | 图片加载 | `AsyncImage`（头像、缩略图） |
| `io.coil-kt:coil-gif` | 2.7.0 | GIF 解码扩展 | 依赖存在；`LinuxDoApp.newImageLoader()` 未显式注册组件 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | 协程调度 | `viewModelScope`、`suspendCancellableCoroutine` |

### 4.3 其他

| 依赖 | 版本 | 用途 | 备注 |
|---|---|---|---|
| `com.google.android.material:material` | 1.12.0 | Material 组件兼容 | 当前代码未直接引用；`HtmlCompat` 来自 `androidx.core` |

### 4.4 调试依赖

| 依赖 | 版本 | 用途 |
|---|---|---|
| `androidx.compose.ui:ui-tooling` | BOM (debug) | Layout Inspector / 预览 |

---

## 5. 测试依赖

| 依赖 | 版本 | 用途 |
|---|---|---|
| `junit:junit` | 4.13.2 | JVM 单测框架 |
| `com.squareup.okhttp3:mockwebserver` | 4.12.0 | 断言真实 HTTP 请求（CSRF 测试） |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | `runTest` |

没有引入 Compose UI Test、Espresso、Robolectric。

---

## 6. Android 构建配置

来自 [app/build.gradle.kts](../../app/build.gradle.kts)：

| 配置 | 值 |
|---|---|
| `namespace` | `org.linuxdo.android` |
| `applicationId` | `org.linuxdo.android` |
| `compileSdk` | 35 |
| `minSdk` | 26 |
| `targetSdk` | 35 |
| `versionCode` / `versionName` | 1 / `1.0.0` |
| `buildConfigField` | `DEFAULT_BASE_URL = "https://linux.do"` |
| Java 兼容性 | 17（source / target） |
| Kotlin jvmTarget | 17 |
| 额外编译参数 | `-opt-in=kotlin.RequiresOptIn` |
| `buildFeatures` | `compose = true`、`buildConfig = true` |
| `testOptions.unitTests.isReturnDefaultValues` | true（JVM 单测中 Android API 返回默认值） |
| `packaging.resources.excludes` | `META-INF/{AL2.0,LGPL2.1}`、`META-INF/DEPENDENCIES`、`META-INF/INDEX.LIST` |

构建类型：

| 类型 | minify | shrinkResources | 签名 |
|---|---|---|---|
| `debug` | false | false | 存在密钥库时使用 release 签名（便于覆盖安装） |
| `release` | true | true | 存在密钥库时签名，否则未签名 |

`debug` 不加 `applicationIdSuffix`：debug 与 release 同包名，签名一致时可直接覆盖安装。

ProGuard 规则（[proguard-rules.pro](../../app/proguard-rules.pro)）：

- 保留 kotlinx.serialization 的 `Companion` / `serializer()` 与 `org.linuxdo.android.**$$serializer`；
- `-dontwarn okhttp3.** / okio.** / org.conscrypt.** / coil.**`。

---

## 7. 清单与权限

来自 [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)：

| 项 | 值 | 说明 |
|---|---|---|
| `INTERNET` | 必需 | 所有 API 与图片 |
| `ACCESS_NETWORK_STATE` | 声明 | 当前代码未直接使用 |
| `POST_NOTIFICATIONS` | 声明 | 当前没有本地通知实现 |
| `usesCleartextTraffic` | false | 仅 https |
| `FileProvider` | `${applicationId}.fileprovider` | 已声明，供未来分享 / 上传使用 |
| 深链 | `https://linux.do/t/` | `VIEW` + `BROWSABLE` |

---

## 8. 外部服务依赖

| 服务 | 地址 | 是否必需 |
|---|---|---|
| Discourse REST API | 默认 `https://linux.do`，可切换 | 是 |
| 阿里 DoH | `https://223.5.5.5/dns-query` | 否（未挂载） |
| Google DoH | `https://dns.google/dns-query` | 否（未挂载） |
| 图标 / 头像 CDN | 由站点返回的 URL 决定 | 是（图片） |

没有统计 SDK、崩溃上报、推送、广告等第三方运行时服务。

---

## 9. 刻意不引入的依赖

| 常见选择 | 本项目 | 原因 |
|---|---|---|
| Retrofit | 不用 | 端点数量有限，`DiscourseApi` 手写 200 行即可；避免注解处理 |
| Hilt / Koin | 不用 | 单例只有 6 个，`AppContainer` 足够 |
| Room | 不用 | 只需键值存储，DataStore + SharedPreferences 够用 |
| Paging 3 | 不用 | 列表分页逻辑简单（page / post_number），手写更可控 |
| Navigation Compose | 引入但未使用 | 页面层级浅，手写状态切换；代价是无返回栈 |
| WebView | 不用 | HTML 用 TextView 渲染，省内存、无 JS 面 |
| 日志 / 监控库 | 不用 | 保持依赖最小；调试时临时加 `HttpLoggingInterceptor` |

---

## 10. 升级依赖时的检查点

1. **Kotlin / Compose 编译器**：两者版本必须一致（本项目均为 2.0.21）；
2. **AGP / Gradle / JDK**：AGP 8.7.3 需要 Gradle 8.9+ 与 JDK 17；
3. **kotlinx.serialization**：运行时与插件版本需匹配，混淆规则不要删；
4. **OkHttp**：`DnsOverHttps` 在 `okhttp-dnsoverhttps` 中，升级需同步；
5. **security-crypto**：`1.1.0-alpha06` 是 alpha，升级时验证 `EncryptedSharedPreferences` 行为；
6. **Coil**：`ImageLoaderFactory` 与 `AsyncImage` API 在 Coil 3 有变化，暂不跨大版本；
7. 升级后跑 `./gradlew :app:testDebugUnitTest` 与 `assembleDebug`（CI 同样顺序）。
