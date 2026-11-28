# 开发约定

## 环境

- JDK 17（AGP 8.7.3 要求）
- Android Studio Ladybug 或更新
- Android SDK 35
- Kotlin 2.0.21 / Compose BOM 2024.10.01

首次构建拉依赖约 400 MB，之后走缓存。

```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 需要签名配置
./gradlew :app:lint                   # 静态检查
./gradlew clean                       # 清缓存（构建莫名失败时先试这个）
```

---

## 代码结构约定

### 包名

```
org.linuxdo.android          ← 根，只放 Application / Activity / 容器
  .data                      ← 仓库 + 存储
  .data.model                ← 纯数据类，无逻辑
  .data.local                ← DataStore 封装
  .data.remote               ← HTTP
  .ui                        ← 根级界面 + 根级 VM
  .ui.<feature>              ← 每个功能一个包：Screen + ViewModel
  .ui.common                 ← 跨功能复用的 Composable
  .ui.theme                  ← 配色 + 基础组件
  .util                      ← 纯函数工具
```

**规则**：`ui/*` 只能依赖 `data` 和 `util`，不能反向依赖。
`data/model` 不能依赖 `data/remote`。

### ViewModel

一律 `StateFlow<XxxState>` + 不可变 `data class`：

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

**不要**用 `mutableStateOf` 做 VM 内部状态——那个是 UI 层的。

### ViewModel 工厂

当前写在 `ui/MainScreen.kt:176`（技术债，见 ROADMAP）：

```kotlin
val vm: FeedViewModel = viewModel(
    key = "feed",
    factory = simpleFactory { FeedViewModel(container.repository) },
)
```

`key` 必须唯一。同一 VM 类型有多个实例时（比如每个话题一个 `TopicDetailViewModel`），
用 `"topic_$topicId"` 这种带参数的形式。

### Composable

- 有状态参数都以 `on` 开头（`onClick` / `onBack` / `onOpenTopic`）
- 私有子组件用 `private fun`，不带 `@Composable` 前缀的 public 导出
- 长列表一定给 `key`：`items(list, key = { it.id })`

**Compose 陷阱**：

```kotlin
// 错：每帧都重新 collect
Text(vm.state.collectAsState().value.error)

// 对：委托属性
val state by vm.state.collectState()
```

```kotlin
// 错：委托值在 lambda 内无法智能转换
if (state.error != null) ErrorBox(state.error)   // 编译错误

// 对
if (state.error != null) ErrorBox(state.error.orEmpty())
```

### 网络

所有请求走 `DiscourseApi`，不要直接 `OkHttpClient.newCall()`。

```kotlin
// 读 + 反序列化
suspend inline fun <reified T> get(path, params): T

// 原样拿字符串（响应的结构不确定时）
suspend fun getRaw(path, params): String

// 写
suspend fun postForm(path, form: Map<String, String>): String
suspend fun putForm(...)
suspend fun deleteForm(...)
```

**错误处理**：所有网络异常都是 `ApiException`，带 `kind`：

```kotlin
enum class Kind { NETWORK, AUTH, PARSE, RATE_LIMIT, SERVER, UNKNOWN }
```

`friendly()` 扩展（`ui/browse/FeedViewModel.kt` 末尾）把它转成中文提示。
新加 ViewModel 时记得复制这个模式，不要直接 `e.message` 弹给用户。

### 数据模型

`kotlinx.serialization`，字段名用 `@SerialName` 映射下划线：

```kotlin
@Serializable
data class Topic(
    val id: Long = 0,
    @SerialName("posts_count") val postsCount: Int = 0,
)
```

**所有字段都要有默认值**。Discourse 不同版本字段会增减，
没默认值会直接崩。

### 时间与格式化

用 `util/TimeFmt.kt`，不要自己 `SimpleDateFormat`：

```kotlin
TimeFmt.relative(topic.bumpedAt)   // "3 小时前"
TimeFmt.dateOnly(user.createdAt)   // "2026-11-25"
count.shortCount()                  // 1234 → "1.2k"
```

`util/Html.kt`：

```kotlin
Html.toPlain(html)          // 去标签、解实体、压空白
Html.firstImageSrc(html)    // 抽第一张图
avatarUrl(template, 96)     // 补 {size} 和域名
absolutize(url)             // 相对地址补全
```

---

## 提交规范

Conventional Commits：

```
feat: 新增分类浏览页
fix: 修复点赞数不刷新
docs: 补充 API 文档
refactor: 抽出 HtmlContent 的预处理逻辑
perf: 列表项加 key 减少重组
test: 补 TimeFmt 单元测试
chore: 升级 Compose BOM
```

**提交前自查**：

```bash
./gradlew :app:assembleDebug    # 必须过
./gradlew :app:lint             # 不能有 error
git diff --stat                 # 确认没夹带无关文件
```

**不要提交**：

- `local.properties`（含 SDK 路径）
- `*.jks` / `*.keystore`
- `build/` 目录
- 任何含 token / cookie / 密码的测试代码

---

## 新增功能的流程

以「分类浏览页」为例：

1. **数据层**：`UserRepository` 加方法
   ```kotlin
   suspend fun topicsInCategory(categoryId: Long, page: Int): TopicListResponse
   ```

2. **模型层**：确认现有 `Category` / `Topic` 够用，不够就加字段（带默认值）

3. **ViewModel**：新建 `ui/category/CategoryViewModel.kt`
   - `StateFlow<CategoryState>`
   - `load()` / `loadMore()` / `refresh()`

4. **界面**：新建 `ui/category/CategoryScreen.kt`
   - 复用 `ui/browse/` 里的 `CenterLoading` / `ErrorBox` / `EmptyBox`
   - 复用 `ui/theme/` 里的 `AvatarFallback` / `CategoryChip`

5. **接线**：`MainScreen.kt` 加入口（底部导航或 FAB 或顶栏按钮）

6. **文档**：如果加了新端点，更新 `docs/API.md`；如果改了架构，更新 `docs/ARCHITECTURE.md`

---

## 调试

### 看请求

`HeaderInterceptor` 里临时加日志：

```kotlin
android.util.Log.d("LD-NET", "${req.method} ${req.url}")
```

或加 OkHttp 的 `HttpLoggingInterceptor`（依赖已有）：

```kotlin
// HttpClients.kt
.addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
    level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
})
```

**记得发布前删掉**——BODY 级别会打出 cookie。

### 看 Compose 重组

Android Studio 的 Layout Inspector → 勾选 "Show recomposition counts"。

### 连不上站点

1. 先在浏览器打开 `https://linux.do` 确认网络通
2. 换 WiFi / 切流量，排除 DNS 污染
3. DoH 已默认挂载（始皇 / 1mol氚，见 `docs/ARCHITECTURE.md`）；确认两个 DoH 域名可达，
   或在 `HttpClients.base` 临时去掉 `.dns(Doh.dns())` 复测
4. 检查是不是 VPN 走了奇怪的路由

### 登录 403

Discourse 登录需要 CSRF 保护。确认：

- `HeaderInterceptor` 给 POST 加了 `Origin` / `Referer`
- cookie jar 里有 `_forum_session`（不只有 `_t`）

---

## 测试

已有三组 JVM 单元测试（`app/src/test/`），全部跑在 JVM 上，不需要模拟器：

| 文件 | 覆盖 |
|---|---|
| `util/HtmlTest.kt` | 实体解码、越界码点、双重转义、`toPlain` 摘要 |
| `util/TopicLinkTest.kt` | 深链 `/t/slug/12345/7` 各种形态与拒绝规则 |
| `util/SiteUrlTest.kt` | 头像 / 图片地址跟随站点（含反向代理子路径） |
| `data/remote/CsrfTest.kt` | CSRF 头注入、403 刷新后单次重放、不误判普通 403 |

```bash
./gradlew :app:testDebugUnitTest          # 全部单元测试
./gradlew :app:testDebugUnitTest --tests 'org.linuxdo.android.util.HtmlTest'
```

`android { testOptions { unitTests { isReturnDefaultValues = true } } }` 是必须的：
`HeaderInterceptor` 会读 `android.os.Build`，不开启时 JVM 测试里访问会抛 "not mocked"。

### 写测试的约定

- 纯逻辑（`util/`、`data/model/`）直接测，不要引入 Android 依赖；
  需要 `android.net.Uri` 之类的地方改成纯字符串实现（参考 `TopicLink`）。
- 网络层用 `MockWebServer` 断言**真实发出的请求**，而不是 mock 掉 `DiscourseApi`——
  CSRF 这类问题只有看实际请求头才能发现。
- 不要在测试里写真实 cookie / token；需要时用 `"TOKEN_A"` 这类明显假值。

### 已知未覆盖

- UI 层（Compose）没有测试，需要 `androidTestImplementation("androidx.compose.ui:ui-test-junit4")`。
- `UserRepository` 的缓存淘汰、`PersistentCookieJar` 的落盘时机没有测试。
