# iOS 样本逆向结论

分析对象：`LINUX DO Community_1.0.5.ipa`（49,054,495 B，sha256 `b9b5d26d…4270`）
方法：纯静态分析（Linux aarch64，无 macOS / 无 iOS 设备）

本文记录**直接影响安卓实现的结论**。完整报告见工作区 `ipa_work/out/REPORT.md`。

---

## 包与签名

| 项 | 值 |
|---|---|
| Bundle ID | `org.linuxdo.community` |
| 版本 | 1.0.5 (13) |
| Team ID | `95UB4DTBJ4`（Apple App Store 签名） |
| App Attest 环境 | `production` |
| 最低系统 | iOS 17.0 / `LSMinimumSystemVersion` 27.0 |
| SDK | `iphoneos27.2`，Xcode 2720 |
| 架构 | arm64，`cryptid=0`（已砸壳） |

### 这个包被重签过

证据链：

1. `_CodeSignature/CodeResources` 有 553 条，逐条 sha256 校验：**530 通过、6 不匹配、15 缺失**
   - 不匹配：3 个 framework + 3 个 appex
   - 缺失：扩展内的 `Assets.car`、`Metadata.appintents`、`cacert.pem`
2. 时间戳断层：资源全 `2026-11-18 11:25~19:25`，Mach-O 与 CodeResources 全 `2026-11-19 04:30`
3. 无 `embedded.mobileprovision`

**对安卓的意义**：无。这是分发渠道的事，不影响 API 行为。

---

## 网络栈（重要）

iOS 版**不用 URLSession**，自己编了 curl + OpenSSL 4.1.0。
`CommunityFoundation.framework` 里的类：

```
CurlMultiActor        CurlRequestHandle      CurlSocketRegistry
CurlSocketSource      CurlResolutionContext  CurlApplicationLifecycle
FoundationHTTPTransfer    FoundationWebSocketTransfer
```

自带一份 `cacert.pem`（235,863 B），错误串里有
`openssl-4.1.0/crypto/asn1/a_verify.c`。

**为什么自建**：为了做 DoH + HTTP/3 + 0-RTT + mTLS 客户端证书。
URLSession 不支持自定义 DNS 解析器的深度集成。

**对安卓的意义**：
安卓侧用 OkHttp 足够——DoH 有现成的 `okhttp-dnsoverhttps`，
mTLS 可以 `sslSocketFactory()` 配，HTTP/3 用 `okhttp` 5.x 或 cronet。
不需要学它自建 curl。

### 内置 DoH 解析器（从二进制提取）

```
https://223.5.5.5/dns-query          阿里
https://223.6.6.6/dns-query          阿里
https://dns.alidns.com/dns-query     阿里
https://doh.pub/dns-query            腾讯
https://doh.360.cn/dns-query         360
https://dns.google/dns-query         Google
https://cloudflare-dns.com/dns-query Cloudflare
https://dns.quad9.net/dns-query      Quad9
https://i4cm5lqxfu.cloudflare-gateway.com/dns-query
https://ios-app.ddd.oaifree.com/query-dns
```

**结论**：linux.do 在某些地区确实会被 DNS 污染，官方 App 才内置这么多备选。
安卓侧的 `Doh.kt` 改用了始皇 / 1mol氚两路 Linux DO 专用 DoH。

### 站点常量

```
https://linux.do                      主站
https://ios.linux.do                  iOS 专用 API 前缀（attest / push）
https://cdn.ldstatic.com              静态资源 CDN
https://cdn3.ldstatic.com
https://linuxdo-uploads.s3.ldstatic.com   上传 S3
https://linux.do/miniapps/            微应用商店页
```

**注意 `ios.linux.do`**：iOS 版的 `/attest/challenge`、`/ios/push/register`、
`/session/current.json` 都走这个子域。安卓侧走主站 `linux.do` 即可。

---

## 认证模型

### App Attest + mTLS

类簇：

```
AttestationService          SystemAppAttestDevice
AttestationStorage          StoredAppAttestation
DeviceCertificateManager    FixedClientIdentityService
FingerprintReporter         SecurityInitializer
```

流程（从字符串和类名还原）：

```
1. SecurityInitializer 启动
2. GET  /attest/challenge           拿 challenge
3. DCAppAttestService.attestKey(_:clientDataHash:)
4. POST /attest/verify              回传 attestation object
5. 领 mTLS 客户端证书（PEM 格式）
6. GET  /attest/dc/renew            定期续期
7. 后续请求带 X-App-Attest-Assertion + X-Device-Check-Token
```

错误码（从二进制提取）：

```
attestation_policy_refused
devicecheck_failed
Attestation verification was not processed
Attestation verification failed
Certificate does not match this device
The client certificate was rejected
App Attest key has not been attested
Server did not confirm attestation
```

存储键：`app_attestation_state_v1`、`fingerprint.lastSubmission.v1`、`mtls_certificate_pem`

### 为什么安卓不做这个

1. **技术不可行**：DeviceCheck / App Attest 是 Apple 独有，密钥绑 Secure Enclave，导不出来
2. **没必要**：Discourse 标准 `POST /session` 用户名密码登录对第三方开放
3. **做了也可能被封**：伪造设备证明违反服务端风控意图

**结论**：安卓走 cookie 会话，完全够用。

---

## 微应用平台（Pajero）

这是 iOS 版最大的自研模块，约 190 个类。

### 四个 renderer

| renderer | 载体 | 关键类 |
|---|---|---|
| `native` | JSON 描述 UI，SwiftUI 渲染 | `MiniAppHostModel` |
| `game` | Canvas + GameController + Worker | `MiniAppGameRuntime` |
| `web` (H5) | WKWebView | `DiscourseMiniAppWebRuntime` |
| `dmnative` | zip 包（HTML+CSS+JS+原生逻辑） | `DiscourseMiniAppPackageCache` |

### native renderer 的控件集

从 `CommunityTool.minipayload` 的 `pages/form.json` 提取：

```
input / multiline / toggle / picker / menu / segmented / date
slider / stepper / progress / column / list / text / button
```

每个控件是 JSON 对象：

```json
{"id": "name", "kind": "input", "text": "名称", "value": "$state.name", "action": "name"}
```

`$state.xxx` 是数据绑定语法，`$item.xxx` 是列表项绑定。

### 加载链路

```
1. MiniAppCatalogStore 拉 catalog
   或读内置 *.minipayload
2. MiniAppPackageStore 下载
   download → verify → decrypt → unpack
   字段：packageURL / packageSize / packagePassword / publicKey / zipSHA256
3. MiniAppWebResources 起本地回环 HTTP
   http://127.0.0.1:PORT
   头：Access-Control-Allow-Private-Network
       Cross-Origin-Resource-Policy
       X-Content-Type-Options
   路径白名单：[A-Za-z0-9_/-]+[.]js$
4. MiniAppLogicRuntime 跑业务 JS（JavaScriptCore）
   context 名：org.linuxdo.miniapp.logicEnvironment
5. 注入 PajeroSDK.js
   原语：__miniSend / __miniSchedule / __miniCancelTimer
   消息上限 16 MB，> 32 KB 走 wire.part 分片（8 KB/片，代理对不切断）
```

### CSP 与网络隔离

```csp
default-src 'none';
script-src <非内联> 'wasm-unsafe-eval';
connect-src 'none';
worker-src 'none';
frame-ancestors 'none';
base-uri 'none'
```

外加内容拦截规则 `miniapp-no-network-v1`：

```json
[{"trigger":{"url-filter":"^https?://"},"action":{"type":"block"}}]
```

**含义**：微应用 JS **不能自己发网络请求**，必须走原生桥 `mini.request()`。
这是安全设计的核心——所有网络行为都在原生侧审计。

### 能力清单

```
currentUser        读当前登录用户
request            HTTP（按 requestOrigins 白名单）
clipboard          读/写剪贴板
screen.setKeepAwake
bluetooth          request / connect / startNotifications / nextNotification
location           经纬度 + altitudeAccuracy
capture            getUserMedia，videoConstraints/audioConstraints
                   media.finishRecording / finishAudio
photos             相册/拍照
native.blob        下载到本地
storage            分账号命名空间 org.linuxdo.micro-app.storage.v1/
                   带 revision 乐观锁，错误码 storageConflict
```

每项都要单独授权，UI 文案在 `PajeroAuthorizationSheet`：
`miniapp-authorize-allow` / `miniapp-authorize-deny`。

年龄门 `PajeroAgeGate`：`under9 / thirteen / sixteen / adult` 分档。

### 签名与信任（关键发现）

`PajeroConfiguration.json` 完整内容：

```json
{"catalogURL": null, "downloadOrigins": [], "trustedKeys": {}}
```

**出厂全空**。这意味着：

- 商店默认关闭（`catalogURL = null`）
- 没有任何可信公钥（`trustedKeys = {}`）
- 不允许从任何域名下载（`downloadOrigins = []`）

包内只有 3 个 `.minipayload` + 1 个 `.dmnative`，全是 demo：

| id | 名称 | renderer | 大小 |
|---|---|---|---|
| `linuxdo.community-tool` | 社区工具箱 | native | 4 文件 / 3.4 KB JS |
| `linuxdo.brick-breaker` | 打砖块 | game | 4.1 KB JS |
| `linuxdo.game2048` | 2048 | game | 5.5 KB JS |
| `Discourse2048.dmnative` | 2048 原生适配 | dmnative | zip 7 文件 |

**所以安卓侧不移植的判断依据**：运行时移植好了，能跑的只有三个 demo。

manifest 签名结构：

```json
{
  "keyID": "...",
  "payload": "...",
  "signature": "..."
}
```

`trustedKeys` 是公钥表。二进制里有 `ed25519` 字符串，
但那也可能是 OpenSSL 自带的算法列表（`strings` 里同一区域有几十个算法名），
**没有确认到具体算法**。

### 判断标准

```bash
curl -sS 'https://linux.do/mini-app-api/apps' \
  -H 'Accept: application/json' \
  -H 'X-Requested-With: XMLHttpRequest' \
  -H 'Cookie: _t=<你的>'
```

- `{"apps":[]}` → 商店没开，不做
- 有内容 → 按 `ROADMAP.md` 的 P3 五步做

---

## 安全相关（其他）

### 几乎无反调试

二进制里**没有**：

- `ptrace` / `PT_DENY_ATTACH`
- `frida` / `cycript` / `substrate` 检测
- `amfid` / 越狱路径检测
- `SIMULATOR` 环境判断

只有 `SecurityViews.swift` 里几个错误状态图标（`xmark.shield.fill` 等）。

**结论**：防线完全在签名 + 服务端 Attest，客户端几乎是裸的。
重签包能跑起来，但 API 会被拒（cdhash 变了，Attest 验证失败）。

### 隐私清单

`PrivacyInfo.xcprivacy` 声明：

```
收集（Linked=true, Tracking=false）：
  DeviceID / UserID / OtherUserContent / ProductInteraction

访问 API：
  UserDefaults      (CA92.1)
  FileTimestamp     (C617.1)
```

### 其他细节

- `NSAppTransportSecurity.NSAllowsArbitraryLoadsInWebContent = true`
  → 网页内容允许非 HTTPS（内嵌浏览器用）
- `ClipboardLinkMonitor` 监听剪贴板 URL，有提示条 `clipboard-link-banner`
- 三级缓存：`TopicListCache` / `SiteCacheEntity` / `DecodedImageCache`，都用 SwiftData

---

## 对安卓的取舍总结

| iOS 做法 | 安卓选择 | 理由 |
|---|---|---|
| 自建 curl + OpenSSL | OkHttp | 功能等价，维护成本低得多 |
| DoH 10 路备用 | DoH 2 路（始皇 + 1mol氚）| 只接管 linux.do，双路失败回落系统 DNS |
| App Attest + mTLS | 标准 cookie 会话 | 技术不可行，且不必要 |
| 微应用平台 | 不移植 | 商店未开，只有 demo |
| cooked HTML 原生解析 | TextView + HtmlCompat | 成本最低，覆盖 90% 内容 |
| SwiftData 三级缓存 | 内存缓存 + DataStore | 当前规模够用 |
| MessageBus 长轮询 | 进通知页刷新 | 省一条常驻连接 |
