# 06 · 网络层参考

网络层只做「把请求可靠地发出去、把响应原样拿回来」，不解释业务语义。
业务语义（登录成功、收藏状态、列表分页）全部在 `UserRepository`。

```text
UserRepository
   │ postForm / putForm / deleteForm / getRaw
┌──▼────────────────────────────────────────────────────────────┐
│ DiscourseApi                                                   │
│  ├─ urlFor()        拼 baseUrl + path + query                  │
│  ├─ attachCsrf()    写请求补 X-CSRF-Token                       │
│  ├─ execute()       2xx 返回 body；403 CSRF 刷新后重放一次       │
│  └─ await()         OkHttp enqueue ↔ suspendCancellableCoroutine│
└──┬────────────────────────────────────────────────────────────┘
   │ OkHttpClient（按 baseUrl 缓存）
┌──▼────────────────────────────────────────────────────────────┐
│ HttpClients.forSite()                                          │
│  ├─ CookieJar        PersistentCookieJar（加密落盘）            │
│  └─ Interceptor      HeaderInterceptor（公共头 / Origin / UA）  │
└──┬────────────────────────────────────────────────────────────┘
   │ HTTPS
Discourse REST API
```

---

## 1. DiscourseApi

文件：[DiscourseApi.kt](../../app/src/main/java/org/linuxdo/android/data/remote/DiscourseApi.kt)

### 1.1 构造参数

```kotlin
class DiscourseApi(
    private val baseUrlProvider: () -> String,
    private val clientProvider: () -> OkHttpClient,
    private val csrfProvider: () -> String? = { null },
    private val csrfPersist: (String) -> Unit = {},
)
```

| 参数 | 何时调用 | 为什么是 provider |
|---|---|---|
| `baseUrlProvider` | 每次 `urlFor()` | 切换站点后立即生效，不需要重建 API |
| `clientProvider` | 每次 `execute()` | 按当前站点取对应 OkHttpClient |
| `csrfProvider` | 写请求附加 token 时 | token 会随响应头轮换 |
| `csrfPersist` | `refreshCsrf()` 成功后 | 落到 `SessionStore.csrfTokens`（加密 prefs） |

`baseUrl` / `client` 私有属性只是 provider 的 `trimEnd('/')` 包装。

### 1.2 Json 配置

```kotlin
val json: Json = Json {
    ignoreUnknownKeys = true   // Discourse 插件字段增删
    isLenient = true
    explicitNulls = false
    coerceInputValues = true   // 类型不符用默认值，不崩
    encodeDefaults = false
}
```

`UserRepository` 直接复用该实例。

### 1.3 URL 拼接

`urlFor(path, params)` 规则：

| `path` 形态 | 结果 |
|---|---|
| `http://…` / `https://…` | 原样使用（用于绝对地址） |
| 以 `/` 开头 | `baseUrl + path` |
| 其他 | `baseUrl + "/" + path` |

随后用 `toHttpUrlOrNull()` 解析；失败抛 `ApiException(Kind.UNKNOWN, "无法解析 URL: …")`。
`params` 中值为 `null` 的项会被跳过，非 null 用 `addQueryParameter` 追加。

### 1.4 公开方法

| 方法 | HTTP | body | 说明 |
|---|---|---|---|
| `urlFor(path, params)` | — | — | 公开的 URL 构造，便于测试 / 调试 |
| `refreshCsrf(): String?` | GET `/session/csrf.json` | — | 解析 `csrf` 字段，成功后 `csrfPersist(token)`；失败返回 null |
| `getRaw(path, params)` | GET | — | 返回字符串 |
| `healthCheck(): Boolean` | GET `/site/basic-info.json` | — | 响应含 `"title"` 即 true；**未使用** |
| `postForm(path, form)` | POST | `application/x-www-form-urlencoded` | 返回字符串 |
| `putForm(path, form)` | PUT | 同上 | 返回字符串 |
| `deleteForm(path, form = emptyMap())` | DELETE | 同上 | 返回字符串 |
| `postJson(path, payload)` | POST | `application/json; charset=utf-8` | **未使用** |
| `uploadFile(path, fileName, mime, bytes)` | POST | multipart：`file` + `type=composer` | **未使用**；返回上传 JSON（含 `short_url`） |

### 1.5 执行与 CSRF 重试

```kotlin
private suspend fun execute(req: Request, allowCsrfRetry: Boolean = true): String
```

流程：

1. `isWrite = method !in ("GET", "HEAD")`；
2. 写请求调用 `attachCsrf(req)`：若请求没有 `X-CSRF-Token` 且本地有 token，则补上；
3. `await(sent)` 得到 `(body, code, message)`；
4. `code in 200..299` → 返回 body；
5. 若 `code == 403 && isWrite && allowCsrfRetry && looksLikeCsrfFailure(body)`：
   - `before = csrfProvider()`；
   - `fresh = refreshCsrf()`（GET，不会再触发本分支）；
   - `fresh != null && fresh.isNotBlank() && fresh != before` → **用原始 req 重放一次**
     （`allowCsrfRetry = false`，且原始 req 没有旧 token，`attachCsrf` 会取新 token）；
   - 否则（token 没变 / 取不到）→ 抛出 `Kind.CSRF`；
6. 其他状态码 → `apiError()`。

```text
写请求 ──► 403 + "invalid authenticity token" ──► GET /session/csrf.json
   ▲                                                    │
   │  token 变了（只重放一次）                            │
   └────────────────────────────────────────────────────┘
             token 没变 → ApiException(Kind.CSRF)
普通 403（无 CSRF 特征）→ 不刷新、不重放 → ApiException(Kind.AUTH)
```

`looksLikeCsrfFailure(body)`：大小写不敏感，匹配 `invalid authenticity token` 或包含 `csrf`。

`attachCsrf()`：

```kotlin
if (req.header("X-CSRF-Token") != null) return req      // 调用方显式指定则不覆盖
val token = csrfProvider() ?: return req
return req.newBuilder().header("X-CSRF-Token", token).build()
```

### 1.6 错误分类

```kotlin
private fun apiError(code, body, message, url): ApiException
```

| 条件 | `Kind` | 备注 |
|---|---|---|
| 403 且响应体像 CSRF 失败 | `CSRF` | 与重试分支一致 |
| 401 / 403（其他） | `AUTH` | 无权限、被封禁、未登录 |
| 429 | `RATE_LIMIT` | Discourse 限流 |
| 500..599 | `SERVER` | 服务端错误 |
| 其他 | `UNKNOWN` | 包含 URL 解析失败 |

异常类型：

```kotlin
class HttpException(val code: Int, val body: String, val url: String) : IOException(...)

class ApiException(val kind: Kind, message: String, cause: Throwable? = null) : IOException(...) {
    enum class Kind { NETWORK, AUTH, CSRF, PARSE, RATE_LIMIT, SERVER, UNKNOWN }
    val isCsrfFailure: Boolean get() = kind == Kind.CSRF
}
```

- `ApiException` 的 `cause` 通常是 `HttpException`（保留状态码与响应体）；
- `NETWORK` 由 OkHttp `onFailure` 产生；
- `PARSE` 已定义但当前没有产生点（JSON 解析失败会以 `SerializationException` 冒泡）。

### 1.7 协程桥接

```kotlin
private suspend fun await(req: Request): Triple<String, Int, String> =
    suspendCancellableCoroutine { cont ->
        val call = client.newCall(req)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(c: Call, e: IOException) {
                cont.resumeWithException(ApiException(Kind.NETWORK, e.message ?: "网络请求失败", e))
            }
            override fun onResponse(c: Call, response: Response) {
                response.use { r ->
                    val text = try { r.body?.string().orEmpty() } catch (e: Exception) { "" }
                    cont.resume(Triple(text, r.code, r.message))
                }
            }
        })
    }
```

要点：

- 使用 `enqueue` 而非阻塞 `execute`，不占用协程调度线程；
- 协程取消时 `call.cancel()`，连接随之取消；
- `response.use` 确保 body 关闭；
- 非 2xx 的 body 也会被读取（用于判断 CSRF 特征与错误信息）。

---

## 2. HttpClients

文件：[HttpClients.kt](../../app/src/main/java/org/linuxdo/android/data/remote/HttpClients.kt)

单例 object，负责两件事：基础配置、按站点缓存客户端。

```kotlin
private val base: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

private val cache = ConcurrentHashMap<String, OkHttpClient>()

fun forSite(baseUrl: String, cookieJar: CookieJar, csrfTokens: CsrfTokens): OkHttpClient =
    cache.getOrPut(baseUrl) {
        base.newBuilder()
            .cookieJar(cookieJar)
            .addInterceptor(HeaderInterceptor(baseUrl, csrfTokens))
            .build()
    }

fun invalidate() = cache.clear()
```

| 项 | 说明 |
|---|---|
| 连接池 | 所有同站点客户端共享 `base` 的连接池配置（`newBuilder` 派生，连接池默认共享） |
| 超时 | connect 15s / read 30s / write 30s |
| 重定向 | 跟随 HTTP 与 HTTPS 重定向 |
| 重试 | `retryOnConnectionFailure = true` |
| 缓存 key | `baseUrl` 原字符串（`SessionStore` 已 trimEnd） |
| 为何按站点缓存 | `HeaderInterceptor` 的 `Origin` / `Referer` 在构造时固定 |
| DoH | **没有挂载**；需要时在 `base` 构建链加 `.dns(Doh.dns())` |

图片客户端不在 `HttpClients` 中，而是 `AppContainer.imageHttpClient`（不挂 cookie、独立连接池）。

---

## 3. HeaderInterceptor

文件：[HeaderInterceptor.kt](../../app/src/main/java/org/linuxdo/android/data/remote/HeaderInterceptor.kt)

### 3.1 请求头

| Header | 值 | 应用范围 | 目的 |
|---|---|---|---|
| `Accept` | `application/json` | 所有请求（未显式设置时） | 避免返回 HTML 登录页 |
| `X-Requested-With` | `XMLHttpRequest` | 所有请求 | Discourse 据此走 JSON / AJAX 分支 |
| `Discourse-Logged-In` | `true` | 所有请求 | 站点跳过匿名静态缓存 |
| `User-Agent` | `Mozilla/5.0 (Linux; Android {version}; {model}) … LDAndroid/{versionName} …` | 所有请求 | 服务端区分流量 |
| `X-CSRF-Token` | 当前 token | 写请求（未显式设置且本地有） | 通过 CSRF 校验 |
| `Origin` | 站点根地址 | 写请求 | Discourse 校验来源 |
| `Referer` | `{站点根}/` | 写请求 | 同上 |

「未显式设置」判断使用 `req.header(name) == null`，因此调用方可以覆盖任何默认值。

### 3.2 响应处理

```kotlin
val response = chain.proceed(b.build())
response.header("X-CSRF-Token")?.let { csrfTokens.update(it) }
return response
```

Discourse 会在部分响应里轮换 CSRF token，这里顺手落盘，减少一次 `GET /session/csrf.json`。

### 3.3 备注

- `HeaderInterceptor` 与 `DiscourseApi.attachCsrf()` 都会补 CSRF，但后者先执行，
  因此 `HeaderInterceptor` 通常看到已存在的头并跳过；
- UA 拼接读取 `android.os.Build`，JVM 单测依赖 `unitTests.isReturnDefaultValues = true`；
- 没有日志拦截器；调试时可临时加 `HttpLoggingInterceptor`（依赖已引入，见 `docs/CONTRIBUTING.md`）。

---

## 4. PersistentCookieJar

文件：[PersistentCookieJar.kt](../../app/src/main/java/org/linuxdo/android/data/remote/PersistentCookieJar.kt)

```kotlin
class PersistentCookieJar(
    private val load: () -> List<Cookie>,
    private val save: (List<Cookie>) -> Unit,
) : CookieJar
```

| 成员 | 行为 |
|---|---|
| `store` | `LinkedHashMap<String, Cookie>`，key = `"domain|path|name"` |
| `init` | 构造时 `load()` 全量装入内存 |
| `saveFromResponse(url, cookies)` | cookies 为空直接返回；`expiresAt < now` 视为删除；有变化才 `save(全量快照)` |
| `loadForRequest(url)` | 过滤 `cookie.matches(url)` 且未过期的项 |
| `clear()` | 清空内存并 `save(emptyList())` |
| `snapshot()` | 返回当前全部 cookie（未使用） |

持久化由 `SessionStore` 提供：

```kotlin
PersistentCookieJar(
    load = { decodeCookies(secure.getString(KEY_COOKIES, null)) },
    save = { list -> secure.edit().putString(KEY_COOKIES, encodeCookies(list)).apply() },
)
```

**特点**：

- 只按完整快照落盘，不做增量；cookie 数量少，成本可接受；
- 同名 cookie 以 `domain|path|name` 覆盖；
- 过期 cookie 立即从内存和磁盘移除；
- `_t` 是 HttpOnly，代码不读取其值，只由 OkHttp 自动携带。

---

## 5. CsrfTokens

文件：[CsrfTokens.kt](../../app/src/main/java/org/linuxdo/android/data/remote/CsrfTokens.kt)

```kotlin
class CsrfTokens(load: () -> String?, save: (String?) -> Unit) {
    @Volatile private var cached: String? = load()?.takeIf { it.isNotBlank() }

    fun current(): String?
    fun update(token: String?)   // trim；空串视为 null；同时 save
    fun clear() = update(null)
    val hasToken: Boolean
}
```

| 点 | 说明 |
|---|---|
| 线程可见性 | `cached` 用 `@Volatile`，网络线程与主线程都能读到最新值 |
| 初始化 | 构造时同步从加密 prefs 读取一次 |
| 更新 | `update` 同时更新内存与磁盘 |
| 使用 | `DiscourseApi.csrfProvider` / `HeaderInterceptor` 读；`refreshCsrf` / 响应头写 |
| `hasToken` | 未使用 |

---

## 6. Doh（默认未启用）

文件：[Doh.kt](../../app/src/main/java/org/linuxdo/android/data/remote/Doh.kt)

```kotlin
object Doh {
    private val client: OkHttpClient by lazy { OkHttpClient() }

    private val primary: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(client)
            .url("https://223.5.5.5/dns-query".toHttpUrlOrNull()!!)
            .bootstrapDnsHosts(InetAddress.getByName("223.5.5.5"))
            .build()
    }

    private val fallback: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(client)
            .url("https://dns.google/dns-query".toHttpUrlOrNull()!!)
            .build()
    }

    val resolver: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            try { primary.lookup(hostname) } catch (e: Exception) { fallback.lookup(hostname) }
    }

    fun dns(): Dns = resolver
}
```

| 项 | 值 |
|---|---|
| 主解析器 | `https://223.5.5.5/dns-query`（阿里），用 IP 做 bootstrap，避免解析器域名本身依赖系统 DNS |
| 回落 | `https://dns.google/dns-query` |
| 策略 | 主解析器任何异常都回落 |
| 启用方式 | `HttpClients.base` 构建链加 `.dns(Doh.dns())` |
| 默认关闭原因 | 绕开系统 DNS，在企业网络 / 强制代理 / DNS 正常的环境下可能更慢或失败 |

---

## 7. 一次请求的完整时序

```text
Repository.topicList()
  → DiscourseApi.getRaw("/latest.json", {page:0, per_page:30})
      ├─ urlFor → https://linux.do/latest.json?page=0&per_page=30
      ├─ Request.Builder().get().build()
      └─ execute()
           ├─ isWrite = false → 不 attach CSRF
           ├─ await()
           │    └─ OkHttpClient（forSite 缓存）
           │         ├─ PersistentCookieJar.loadForRequest() → 带 _t
           │         └─ HeaderInterceptor
           │              ├─ Accept / X-Requested-With / Discourse-Logged-In / UA
           │              └─ proceed()
           ├─ 200 → body 字符串
           └─ 非 2xx → apiError()

Repository 解析 JSON
  → json.decodeFromString<TopicListResponse>(body)
```

写请求额外经过：

```text
attachCsrf（旧 token）
  → HeaderInterceptor（Origin / Referer）
  → 403 Invalid Authenticity Token
  → refreshCsrf() → GET /session/csrf.json（无 CSRF 头）
  → 新 token != 旧 token → 重放原始 Request（新 token）
  → 2xx → 成功
```

---

## 8. 网络层注意事项

| 项 | 现状 |
|---|---|
| 请求日志 | 未挂载 `HttpLoggingInterceptor`；需要时手动添加，发布前移除（BODY 会打印 cookie） |
| 证书固定 | 无 |
| 代理支持 | 依赖系统代理；代码未显式设置 |
| 明文 HTTP | Manifest `usesCleartextTraffic=false`，http 站点不可用 |
| 重定向携带 cookie | OkHttp 默认行为，`followRedirects=true` |
| 超时 | 由 `HttpClients.base` 统一设置；图片客户端是另一套 |
| 取消 | 协程取消会取消 OkHttp Call |
