# 架构说明

## 分层

```
┌─────────────────────────────────────────┐
│  ui/          Compose 界面 + ViewModel   │
├─────────────────────────────────────────┤
│  data/        UserRepository（业务）      │
├─────────────────────────────────────────┤
│  data/remote  DiscourseApi（HTTP）        │
│  data/local   DataStore（草稿/历史）      │
├─────────────────────────────────────────┤
│  OkHttp + kotlinx.serialization          │
└─────────────────────────────────────────┘
```

没有 DI 框架。`AppContainer` 手写了 5 个单例：

```kotlin
class AppContainer(context: Context) {
    val sessionStore: SessionStore        // 站点地址 + cookie
    val imageHttpClient: OkHttpClient     // 图片专用，不挂 cookie
    val api: DiscourseApi
    val repository: UserRepository
    val draftStore: DraftStore
    val searchHistoryStore: SearchHistoryStore
}
```

`LinuxDoApp` 里创建一次，`MainActivity` 通过 `(application as LinuxDoApp).container` 取。

ViewModel 通过 `simpleFactory { XxxViewModel(container.repository) }` 构造，
工厂函数在 `ui/MainScreen.kt:176`（**技术债：应移到 `ui/common/`**）。

---

## 数据流

### 列表页

```
FeedScreen
  ↓ LaunchedEffect(Unit) → vm.ensureLoaded()
FeedViewModel.load()
  ↓ repo.topicList(filter, page=0, categoryId, period)
UserRepository.topicList()
  ↓ 查内存缓存（listCacheKey）→ 命中直接返回
  ↓ 未命中 → api.getRaw("/latest.json?page=0&per_page=30")
DiscourseApi.getRaw()
  ↓ OkHttp + HeaderInterceptor + CookieJar
  ↓ json.decodeFromString<TopicListResponse>
FeedState.topics 更新 → Compose 重组
```

缓存 key：`"${filter.path}|${categoryId ?: -1}|${period?.value ?: "-"}|$page"`

**失效时机**：下拉刷新（`forceReload=true`）、切换站点（`invalidateLists()`）、退出登录。

### 详情页

```
TopicDetailScreen
  ↓ viewModel(key = "topic_$topicId")
TopicDetailViewModel.load()
  ↓ repo.topic(topicId)  → GET /t/{id}.json?include_raw=true
  ↓ 得到 postStream.posts（前 20 楼）+ postStream.stream（全部 post id）
  ↓ loadedPostIds 记下已有 id
往下滑到底
  ↓ vm.loadMore()
  ↓ lastPostNumber + 1 → GET /t/{id}.json?post_number=N
  ↓ 过滤掉 loadedPostIds 里已有的，追加到 posts
```

**为什么不用 page**：Discourse 的话题是稀疏流。
`stream` 里可能有被删的楼层，用 `page * 20` 会错位。
必须用 `post_number` 定位。

**点赞**：乐观更新。先在 `interactions: Map<Long, PostInteraction>` 里改，
请求失败回滚。服务端返回的 count 优先于本地推算。

---

## 网络层

### HeaderInterceptor

Discourse 有几点必须对齐：

| Header | 值 | 原因 |
|---|---|---|
| `X-Requested-With` | `XMLHttpRequest` | 否则未登录时返回 HTML 登录页而不是 401 |
| `Accept` | `application/json` | 同上 |
| `Discourse-Logged-In` | `true` | 站点据此跳过匿名静态缓存 |
| `Origin` / `Referer` | 站点根地址 | 写操作（非 GET）必须带，Discourse 校验 CSRF |
| `User-Agent` | 带 `LDAndroid/1.0.0` | 便于服务端区分流量 |

### Cookie

Discourse 的会话是 `_t` cookie（HttpOnly，`document.cookie` 读不到）。

`PersistentCookieJar` 存内存 `LinkedHashMap<"$domain|$path|$name", Cookie>`，
变更时全量序列化到 `EncryptedSharedPreferences`。

序列化格式用 `\u0001` 分隔 7 个字段（name / value / domain / path / expiresAt / secure / httpOnly），
`\n` 分隔条目。选择自定义格式而不是 JSON 是为了避免 `Cookie` 类没有 serializer。

**加密**：`MasterKey` 用 `AES256_GCM`，由 Android Keystore 托管。
`EncryptedSharedPreferences` 的 key 用 `AES256_SIV`、value 用 `AES256_GCM`。

### DoH

`data/remote/Doh.kt` 实现了双路解析：

```kotlin
primary  = https://linuxdo.ddd.oaifree.com/query-dns   // 始皇，子域可任意替换
fallback = https://edge.1molchuan.top/linuxdo          // 1mol氚
```

已挂载到 `HttpClients.base`（`.dns(Doh.dns())`）。

**作用域**：只有 `linux.do` 与 `*.linux.do` 走 DoH；自建站点、图片 CDN
仍用系统 DNS，避免把别人的解析也劫持了。两路 DoH 都失败时回落
`Dns.SYSTEM`，不会因为 DoH 不可用导致 App 断网。

DoH 客户端本身用系统 DNS 解析 `ddd.oaifree.com` / `1molchuan.top`，
超时各 5s。

---

## JSON 处理

`kotlinx.serialization`，`Json` 配置：

```kotlin
Json {
    ignoreUnknownKeys = true    // Discourse 字段会随插件增减
    isLenient = true            // 容忍格式瑕疵
    explicitNulls = false       // null 字段不参与序列化
    coerceInputValues = true    // 类型不符时用默认值而非崩溃
    encodeDefaults = false
}
```

**特殊处理：`tags` 字段**

Discourse 的 `tags` 有三种形态：

```json
["ai-plugin"]                                          // 话题详情
[{"id":543,"name":"ai-plugin","slug":"ai-plugin"}]     // 列表
[]                                                     // 无标签
```

`TagOrStringSerializer` 用手写 `KSerializer` 兼容两者：
读到 `JsonPrimitive` 就当字符串，读到 `JsonObject` 就取 `name` / `id`。

**`avatar_template`**

```json
"/user_avatar/meta.discourse.org/sam/{size}/102149_2.png"
```

`{size}` 要替换成像素值（48 / 96 / 192），再补 `https://host` 前缀。
工具函数在 `util/Html.kt` 的 `avatarUrl()`。

---

## HTML 渲染

`cooked` 是服务端渲染好的 HTML。三条路：

| 方案 | 优点 | 缺点 | 选了 |
|---|---|---|---|
| WebView | 完整支持 | 内存大、有 JS 面、滚动嵌套 | ✗ |
| TextView + HtmlCompat | 轻、无 JS | 不支持 img/table/iframe | ✓ |
| 自己写解析器 | 完全可控 | 工作量大 | ✗ |

`ui/common/HtmlText.kt` 的做法：

```kotlin
AndroidView(factory = { TextView(it) }) { tv ->
    tv.text = HtmlCompat.fromHtml(preprocessed, FROM_HTML_MODE_COMPACT)
    tv.movementMethod = LinkMovementMethod.getInstance()  // 链接可点
}
```

**预处理**（`HtmlContent` 里的 `remember(html)` 块）：
- 剥掉 `<img>` → 后续版本改成切块渲染（见 ROADMAP P0-1）
- `<video>` → `[视频]`
- `<iframe>` → `[嵌入内容]`

**丢失的样式**：`HtmlCompat` 只认 basic tags。
`<pre><code>` 会保留等宽但不带背景色，需要的话用 `SpannableStringBuilder` 手动加。

---

## 状态管理

统一 `StateFlow<XxxState>` + `data class` 拷贝：

```kotlin
private val _state = MutableStateFlow(FeedState())
val state: StateFlow<FeedState> = _state.asStateFlow()

_state.update { it.copy(loading = true) }
```

Compose 側通过 `ui/browse/ComposeShims.kt` 的 `collectState()` 转成 `State<T>`：

```kotlin
val state by vm.state.collectState()
```

**注意**：委托属性 `by` 拿到的值在 lambda 里**不能智能转换**。
所以 `state.error` 已经判过 `!= null`，传参时还是要 `.orEmpty()`
（`FeedScreen.kt:93`、`TopicDetailScreen.kt:167` 都是这个处理）。

---

## 时间与数字

`util/TimeFmt.kt`：

```kotlin
TimeFmt.relative("2026-11-24T23:38:07.053Z")  // "3 小时前"
TimeFmt.absolute(...)                          // "2026-11-25 07:38"
TimeFmt.dateOnly(...)                          // "2026-11-25"
Int.shortCount()                               // 1234 → "1.2k"
```

解析用 `OffsetDateTime.parse` 兜底 `Instant.parse`（Discourse 有时省略时区）。

---

## 主题

`ui/theme/Theme.kt`：

- 强调色 `#33D17A`（终端绿，对齐社区调性）
- 深色背景 `#0F1115`（比纯黑柔和）
- `dynamicColor = false` 默认关闭：Material You 会把品牌色冲掉

头像兜底 `AvatarFallback`：无图时用用户名 hash 派生 HSL 渐变色 + 首字母。
这样列表在弱网下也不会是一片灰块。

分类色 `parseHexColor("0088CC")` 解析 Discourse 的 6 位 hex，
`onColorFor()` 按亮度决定前景黑/白（供 `CategoryChip` 用）。
