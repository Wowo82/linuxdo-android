# LINUX DO · Android 客户端

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202024.10-4285F4?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android)](https://developer.android.com)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

> 文档入口：[docs/INDEX.md](docs/INDEX.md) · 路线图 [docs/ROADMAP.md](docs/ROADMAP.md) · API 参考 [docs/API.md](docs/API.md)

`org.linuxdo.android` — 基于 Discourse REST API 的原生安卓客户端，
按 iOS 版 `LINUX DO Community 1.0.5` 的功能结构复刻，Kotlin + Jetpack Compose。

默认站点 `https://linux.do`，可在「我的 → 服务器地址」改成任意自建 Discourse。

---

## 构建

环境：Android Studio Ladybug 及以上 / JDK 17 / Android SDK 35。

```bash
# 方式一：Android Studio 打开本目录，直接 Run
# 方式二：命令行
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

首次打开会从 Gradle 拉 AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.10.01，约 400 MB 依赖。

如果需要 release 包，先配置签名：

```kotlin
// app/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            storeFile = file("../keystore.jks")
            storePassword = System.getenv("KS_PASS")
            keyAlias = "linuxdo"
            keyPassword = System.getenv("KEY_PASS")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("release") }
    }
}
```

---

## 功能对照（vs iOS 版）

| 模块 | iOS 版 | 本实现 |
|---|---|---|
| 话题列表 | latest / hot / new / unread / top + 周期筛选 | ✅ 同构，含分类维度 |
| 话题详情 | 楼层流、回复、点赞、收藏、编辑 | ✅ 回复 / 点赞（乐观更新）/ 收藏 |
| 发帖 | 标题 + 分类 + Markdown 正文 | ✅ |
| 搜索 | 全文 + 用户 + 分类 | ✅ 300ms 防抖、历史记录 |
| 通知 | 分组未读、按类型图标 | ✅ 类型映射中文标签 |
| 登录 | cookie 会话 + App Attest | ✅ cookie 会话（见下文「差异」） |
| 深色模式 | 跟随系统 | ✅ 跟随系统 |
| 我的 | 资料、徽章、统计 | ✅ 资料 + TL + 统计 |
| 微应用（Pajero） | JSC 运行时 + 原生渲染 | ❌ 未移植 |
| App Attest / mTLS | DeviceCheck + 客户端证书 | ❌ 不需要（走标准 cookie 登录） |

---

## 架构

```
app/src/main/java/org/linuxdo/android/
├─ LinuxDoApp.kt            Application，Coil ImageLoader 配置
├─ AppContainer.kt          依赖容器（手写，无 DI 框架）
├─ MainActivity.kt          单 Activity + Compose
├─ data/
│  ├─ SessionStore.kt       站点地址 + cookie（EncryptedSharedPreferences）
│  ├─ UserRepository.kt     业务层：列表 / 详情 / 发帖 / 搜索 / 通知
│  ├─ model/Models.kt       kotlinx.serialization 数据模型
│  ├─ local/
│  │  ├─ DraftStore.kt      草稿 + 阅读位置（DataStore）
│  │  └─ SearchHistoryStore.kt
│  └─ remote/
│     ├─ DiscourseApi.kt    HTTP 封装（GET/POST/PUT/DELETE/上传）
│     ├─ HeaderInterceptor.kt  Discourse 必需请求头
│     ├─ HttpClients.kt     按站点缓存的 OkHttpClient
│     └─ Doh.kt             DoH 解析器（阿里 + Google 双路）
├─ ui/
│  ├─ MainScreen.kt         Scaffold + 底部导航 + FAB
│  ├─ MainViewModel.kt      站点信息 / 当前用户 / 未读角标
│  ├─ browse/FeedScreen    话题列表（Tab 筛选 + 下拉刷新 + 无限滚动）
│  ├─ topic/                详情 + 发帖
│  ├─ search/               搜索
│  ├─ notifications/        通知
│  ├─ profile/              我的 / 登录 / 服务器设置
│  ├─ common/HtmlText.kt    cooked HTML → TextView 渲染
│  └─ theme/                配色 + 头像兜底 + 分类标签
└─ util/
   ├─ TimeFmt.kt            相对时间、数字缩写
   └─ Html.kt               HTML 转纯文本 / 抽首图
```

### 关键设计取舍

**1. HTML 用 TextView 而不是 WebView**

`cooked` 是服务端渲染好的 HTML。iOS 版用原生解析 + SwiftUI 排版；
Android 侧用 `TextView` + `HtmlCompat` 是最小成本的等价方案：
支持 p / strong / a / ul / pre / code / blockquote，不支持 table / iframe / 视频（留「浏览器打开」兜底）。
省内存、无 JS 执行面。

**2. Cookie 是唯一登录态**

Discourse 的会话是 `_t` cookie（HttpOnly）。iOS 版额外叠了 App Attest + mTLS 客户端证书，
那是官方 App 专属通道；第三方客户端走标准 `POST /session` 拿 cookie 即可，
不需要也不该伪造设备证明。

cookie 存 `EncryptedSharedPreferences`（AES256-GCM），key 由 Android Keystore 托管。

**3. DoH 默认关闭，配置保留**

`Doh.kt` 实现了阿里 / Google 双路 DoH。当前没有挂到 OkHttpClient 上——
因为 DoH 会让所有请求绕开系统 DNS，在部分企业网络下反而变慢或失败。
需要时在 `HttpClients.base` 加一行 `.dns(Doh.dns())` 即可启用。

**4. 分页用 post_number 而不是 page**

Discourse 的话题帖子流是稀疏的：`topic.json` 只给前 20 楼 + 全部 post id（stream）。
继续加载要带 `?post_number=N` 从指定楼层往后取。列表页才是 page 分页。

**5. 列表内存缓存**

`UserRepository` 里按 `filter|categoryId|period|page` 缓存响应，
返回上一页时不重新请求；下拉刷新时 `forceReload=true` 绕过。

---

## 与 IOS 样本的差异（逆向结论直接映射）

分析 iOS 二进制得到的三件事，这里刻意没做：

1. **App Attest / DeviceCheck / mTLS**（`AttestationService`、`DeviceCertificateManager`）
   — 绑定 Apple 生态，安卓无对应能力；官方 App 用它做反爬与设备风控，
   第三方客户端走标准 cookie 反而更透明。

2. **Pajero 微应用平台**（约 190 个类、JSC 运行时、本机回环资源服务、签名包分发）
   — 这是 iOS 版最大的自研模块，但服务端 `PajeroConfiguration.json` 里
   `catalogURL=null / trustedKeys={}`，商店默认关闭，只有包内 3 个示例小程序。
   移植价值低，先不做。

3. **MessageBus 长轮询**（`MessageBusService`、`/websocket-authorization`）
   — 实时未读与在线状态依赖它。本实现用「进通知页时刷新」替代，
   省掉一条常驻连接。需要可加 `GET /message-bus/{clientId}/poll`。

---

## 已知限制

- 图片附件显示为占位（`<img>` 在 TextView 里渲染不了），点开可跳浏览器
- onebox（链接预览卡片）、投票、数学公式未渲染
- 上传附件接口已封装（`DiscourseApi.uploadFile`）但发帖 UI 未接
- 未实现私信（PM）、聊天（Chat 插件）、审核队列
- 没有离线缓存正文，断网只能看列表缓存
- 自建站点目前只支持 https（`usesCleartextTraffic=false`）

## 写操作与 CSRF

Discourse 对 browser session 的**非 GET** 请求强制校验 `X-CSRF-Token`，
只带 cookie + `X-Requested-With` 不豁免。实现方式：

1. 登录成功后 `GET /session/csrf.json` 取 token，和 cookie 一起存进
   EncryptedSharedPreferences；
2. `HeaderInterceptor` 对写请求补 `X-CSRF-Token`（读请求不加）；
3. 写请求收到 403 且响应里像 CSRF 失败时，刷新 token 后**重放一次**，
   token 没变化则不重放，避免死循环；
4. 普通无权限的 403 归为 `ApiException.Kind.AUTH`，不会误判成 CSRF。

## 深链

`https://linux.do/t/{slug}/{id}/{post}` 由 `util/TopicLink.kt` 解析后，
直接打开详情页并滚到指定楼层。解析是纯字符串实现，方便单测。


---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/INDEX.md](docs/INDEX.md) | 文档索引、快速定位表 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 分层、数据流、网络层、HTML 渲染、状态管理 |
| [docs/API.md](docs/API.md) | Discourse 端点参考（含实际响应） |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 后续开发路线 P0-P3 + 技术债 |
| [docs/REVERSE-ENGINEERING.md](docs/REVERSE-ENGINEERING.md) | iOS 样本逆向结论与取舍依据 |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | 编码规范、提交规范、调试 |
| [docs/wiki/README.md](docs/wiki/README.md) | Code Wiki：模块职责、关键类与函数、依赖关系、构建运行 |

## 许可

代码：[MIT](LICENSE)。
LINUX DO 站点内容版权归原作者，本项目不附带任何站点数据。
