# 12 · 测试

## 1. 测试策略

| 项 | 现状 |
|---|---|
| 测试类型 | 仅 JVM 单元测试（`app/src/test`） |
| 运行环境 | 本机 / CI 的 JVM，不需要模拟器 |
| 框架 | JUnit 4 + `kotlinx-coroutines-test` + OkHttp `MockWebServer` |
| UI 测试 | 无（未引入 Compose UI Test / Espresso） |
| 测试规模 | 4 个文件，约 365 行 |

`app/build.gradle.kts` 中必须保留：

```kotlin
testOptions {
    unitTests {
        isReturnDefaultValues = true
    }
}
```

原因：`HeaderInterceptor` 会读取 `android.os.Build.VERSION.RELEASE` / `MODEL`，
不开这个开关时 JVM 测试访问 Android stub 会抛 "not mocked"。

---

## 2. 测试文件与覆盖点

| 文件 | 被测对象 | 用例数 | 重点 |
|---|---|---|---|
| [HtmlTest.kt](../../app/src/test/java/org/linuxdo/android/util/HtmlTest.kt) | `Html` | 8 | 实体解码安全、`toPlain`、首图提取 |
| [TopicLinkTest.kt](../../app/src/test/java/org/linuxdo/android/util/TopicLinkTest.kt) | `TopicLink` | 8 | 深链各种形态与拒绝规则 |
| [SiteUrlTest.kt](../../app/src/test/java/org/linuxdo/android/util/SiteUrlTest.kt) | `avatarUrl` / `absolutize` | 7 | 地址跟随站点、反向代理子路径 |
| [CsrfTest.kt](../../app/src/test/java/org/linuxdo/android/data/remote/CsrfTest.kt) | `DiscourseApi` + `HeaderInterceptor` | 6 | 真实请求头、403 刷新重放、普通 403 不误判 |

---

## 3. `HtmlTest`

| 用例 | 断言 |
|---|---|
| `valid numeric entities decode` | `&#65;` → `A`；`&#x41;` → `A`；`&#x1F600;` → emoji |
| `out of range numeric entity is dropped` | `&#1114112;`、`&#9999999;`、`&#x110000;` → 空串，不抛异常 |
| `double escaped out of range entity does not throw` | `&amp;#1114112;`、`&amp;#x110000;` → 空串 |
| `double escaped valid entity keeps literal` | `&amp;#65;` → 字面量 `&#65;`，不二次解码 |
| `named entities decode` | `&lt;`、`&amp;`、`&gt;`、`&quot;`、`&nbsp;` 正确 |
| `toPlain does not crash on out of range entity` | `<p>hi &#1114112; there</p>` → `hi there` |
| `toPlain strips tags and collapses blank lines` | 段落换行、`pre/code`、`script` 移除 |
| `firstImageSrc picks first img` | 提取第一个 `src`；无图 → null |

这些用例固定了「实体解码顺序」这一高风险逻辑，修改 `Html.unescape` 时必须保持通过。

---

## 4. `TopicLinkTest`

| 用例 | 输入 → 期望 |
|---|---|
| `plain absolute url` | `https://linux.do/t/12345` → `(12345, null)` |
| `slug url` | `/t/some-slug-here/12345` → `(12345, null)` |
| `slug url with post number` | `/t/some-slug/12345/7` → `(12345, 7)` |
| `json suffix and query and fragment` | `/t/slug/99.json?u=foo` → `(99, null)`；`/t/slug/99/3.json#post_3` → `(99, 3)` |
| `scheme-relative and no scheme` | `//linux.do/t/7`、`linux.do/t/7` → `(7, null)` |
| `slug containing digits still resolves topic id` | `/t/release-2024-notes/2024` → `(2024, null)` |
| `non topic paths are rejected` | `/u/...`、`/c/...`、`/`、null、空串 → null |
| `zero and negative ids are rejected` | `/t/0`、`/t/slug/0/5` → null |

`TopicLink` 刻意不使用 `android.net.Uri`，因此这些用例可以在纯 JVM 上跑。

---

## 5. `SiteUrlTest`

| 用例 | 断言 |
|---|---|
| `avatar template uses given site` | 同一个模板分别落到 `linux.do` 与自建域名 |
| `avatar protocol relative and absolute` | `//cdn...` → `https://cdn...`；绝对 URL 原样 |
| `avatar null or blank` | null / 空白 → null |
| `absolutize keeps absolute urls` | http(s) 与协议相对地址原样 / 补 https |
| `absolutize prefixes site paths` | `/uploads` 与 `uploads` 都拼当前站点 |
| `absolutize respects reverse-proxy subpath` | `https://host/bbs` + `/uploads/a.png` → `https://host/bbs/uploads/a.png` |
| `absolutize does not double slash` | `https://host/` + `/u/a.png` → `https://host/u/a.png` |

回归目标：避免再次把站点域名写死为 `https://linux.do`。

---

## 6. `CsrfTest`

使用 `MockWebServer` 断言**真实发出的请求**，而不是 mock 掉 `DiscourseApi`。

### 6.1 测试装置

```kotlin
private fun apiWith(tokens: CsrfTokens): DiscourseApi {
    val base = server.url("/").toString().trimEnd('/')
    client = OkHttpClient.Builder()
        .addInterceptor(HeaderInterceptor(base, tokens))
        .build()
    return DiscourseApi(
        baseUrlProvider = { base },
        clientProvider = { client },
        csrfProvider = { tokens.current() },
        csrfPersist = { tokens.update(it) },
    )
}
```

`tearDown()` 先 `connectionPool.evictAll()` + `dispatcher.executorService.shutdown()`，
再 `server.shutdown()`，并用 `runCatching` 兜住收尾噪音（keep-alive 连接会让 shutdown 等 5 秒）。

### 6.2 用例

| 用例 | 场景 | 断言 |
|---|---|---|
| `write requests carry csrf token and origin` | token = `TOKEN_A`，POST `/posts` | 请求头含 `X-CSRF-Token: TOKEN_A`、`Origin`、`Referer: {base}/`、`X-Requested-With: XMLHttpRequest` |
| `read requests never carry csrf token` | token = `TOKEN_A`，GET `/latest.json` | 无 `X-CSRF-Token`；`Discourse-Logged-In: true`；`Accept: application/json` |
| `403 csrf failure refreshes token and replays once` | 旧 token `STALE` → 403 Invalid Authenticity Token → `{"csrf":"FRESH"}` → 200 | 最终成功；token 变为 `FRESH`；三个请求依次带 `STALE`、`/session/csrf.json`、`FRESH` |
| `unchanged token does not loop` | 403 → `/session/csrf.json` 返回同一个 token | 抛 `ApiException(Kind.CSRF)`；总请求数 2（原请求 + 取 token），不重放 |
| `plain permission 403 is not treated as csrf` | 403 `{"errors":["您没有权限"]}` | `Kind.AUTH`；总请求数 1，不去换 token |
| `no token stored means no header but origin still set` | token = null，POST | 无 `X-CSRF-Token`；`Origin` 仍存在 |

这些用例固定了 06 §1.5 的重试状态机，是网络层最重要的回归保护。

---

## 7. 运行测试

```bash
# 全部 JVM 单测
./gradlew :app:testDebugUnitTest

# 单个测试类
./gradlew :app:testDebugUnitTest --tests 'org.linuxdo.android.util.HtmlTest'

# 带堆栈（CI 使用）
./gradlew :app:testDebugUnitTest --stacktrace

# 测试报告
open app/build/reports/tests/testDebugUnitTest/index.html
```

Android Studio 中可直接点击测试类 / 用例左侧的运行按钮。

CI（`.github/workflows/build.yml`）在打包前执行 `testDebugUnitTest`，失败时上传
`app/build/reports/tests/**` 并停止出包。

---

## 8. 写测试的约定

1. **纯逻辑优先**：`util/`、`data/model/` 的逻辑直接测，不引入 Android 依赖；
   需要 `Uri` 等 Android API 的地方改成纯字符串实现（参考 `TopicLink`）。
2. **网络用 MockWebServer**：断言实际请求头 / 路径 / 次数，而不是 mock `DiscourseApi`——
   CSRF 这类问题只有看真实请求才能发现。
3. **不写真实凭据**：token / cookie 使用 `TOKEN_A`、`STALE` 这类明显假值。
4. **测试命名**：JUnit 4 方法名用反引号描述行为，例如
   `` `403 csrf failure refreshes token and replays once` ``。
5. **协程**：使用 `runTest`，不要 `runBlocking`。
6. **隔离服务器**：每个用例 `MockWebServer` 独立启动；收尾时先清连接池再 shutdown。

---

## 9. 覆盖缺口

| 未覆盖区域 | 风险 | 建议 |
|---|---|---|
| Compose UI | 布局 / 交互回归靠人工 | 引入 `ui-test-junit4`，先覆盖登录、列表、详情点赞 |
| `UserRepository` 列表缓存 | LRU 淘汰、key 组合、forceReload 行为 | 用 fake `DiscourseApi` 或 MockWebServer 测请求次数 |
| `PersistentCookieJar` | 过期删除、同名覆盖、落盘时机 | 纯 JVM 测试 `saveFromResponse` / `loadForRequest` |
| `DraftStore` / `SearchHistoryStore` | 分隔符解析、20 条上限、顺序 | Robolectric 或抽象 DataStore 接口 |
| `TimeFmt` | 边界（59s / 60s / 30 天 / 365 天）、时区 | 注入固定 `Instant` 或改造成可测试时钟 |
| 模型反序列化 | 真实 Discourse 响应字段缺失 / 类型变化 | 保存脱敏 JSON fixture 做快照测试 |
| `MainViewModel.boot` / `changeServer` | 启动失败、切站点状态 | 配合 fake repository |
| CSRF 响应头轮换 | `X-CSRF-Token` 响应头更新路径 | 在 `CsrfTest` 增加响应头用例 |

---

## 10. 测试与代码结构的对应

```text
app/src/test/java/org/linuxdo/android/
├── util/
│   ├── HtmlTest.kt        → util/Html.kt
│   ├── TopicLinkTest.kt   → util/TopicLink.kt
│   └── SiteUrlTest.kt     → util/Html.kt 的 avatarUrl / absolutize
└── data/remote/
    └── CsrfTest.kt        → data/remote/DiscourseApi.kt + HeaderInterceptor.kt + CsrfTokens.kt
```

新增纯逻辑时，优先在对应包下补测试；新增网络行为时，优先扩展现有 `MockWebServer`
测试而不是新建一套装置。
