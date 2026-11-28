# 后续开发路线

当前版本：`1.0.0`（初始可用版本）
状态：话题浏览 / 详情 / 发帖 / 搜索 / 通知 / 登录 六条主线已通

---

## P0 — 影响可用性，先做

### 1. 图片附件显示

**问题**：`cooked` 里的 `<img>` 在 `ui/common/HtmlText.kt` 里被整段剥掉了，
帖子里的截图完全看不见。这是当前最大的体验缺口。

**方案**：不要试图让 TextView 画图。改成「分块渲染」：

```
cooked HTML
  → 按 <img> 切成文本段 / 图片段
  → 文本段用 HtmlContent（现有实现）
  → 图片段用 AsyncImage，点开进全屏预览
```

**改动点**：
- `util/Html.kt` 加 `fun splitByImages(html: String): List<Block>`，
  `Block` 是 `Text(html)` 或 `Image(url, width, height)` 的 sealed class
- `ui/common/HtmlText.kt` 的 `HtmlContent` 改成遍历 Block 输出
- 新增 `ui/common/ImageBlock.kt`，含全屏查看（`Dialog` + 双指缩放）

**注意**：Discourse 的图片有 `_2_690x228` 这类缩略图后缀，
优先取 `src` 里带 `optimized` 的版本，原图太大。

---

### 2. 草稿自动保存

`DraftStore` 已经写好但**没有任何地方调用**。

**需求**：
- 发帖页 / 回复框输入时，每 2 秒或失焦时存草稿
- 重进话题时恢复，顶部提示「已恢复草稿」
- 发送成功后清掉

**key 约定**（对齐 iOS 版）：
- 回复：`topic_{topicId}`，同时记 `replyToPostNumber`
- 发帖：`new_topic_{categoryId}`

**改动点**：
- `TopicDetailViewModel` 注入 `DraftStore`，`init` 里读，`sendReply` 后删
- `ComposerScreen` 需要改成有 ViewModel 的形态（现在全是不受控 remember 状态）

---

### 3. 上传附件

`DiscourseApi.uploadFile()` 已实现但没有 UI 入口。

**Discourse 上传流程**：
```
POST /uploads.json  (multipart: file, type=composer)
  → 返回 { url, short_url, short_path }
  → 把 short_url 拼进正文：![name|widthxheight](upload://xxx)
  → 再 POST /posts
```

**改动点**：
- `ComposerScreen` / 回复框加「选图」按钮，`ActivityResultContracts.GetMultipleContents`
- 上传进度条（OkHttp 的 `RequestBody.writeTo` 里回调）
- 上传完成后在光标处插入 markdown

---

## P1 — 功能补齐

### 4. 分类浏览页（已完成）

底部导航新增「版块」tab：`/categories.json?include_subcategories=true`
展示顶级分类 + 子版块，点击进入 `/c/{slug}/{id}/l/{filter}.json` 的分类话题流。

### 5. 私信（PM）

Discourse 的 PM 就是 `archetype: "private_message"` 的话题，复用详情页即可。

**端点**：
- 列表：`/topics/private-messages/{username}.json`
- 详情：同一个 `/t/{id}.json`
- 新建：`POST /posts` 带 `archetype=private_message` + `target_recipients=username`

**改动点**：
- `UserRepository` 加 `privateMessages(username, page)`
- 底部导航「通知」页加 tab 切换：通知 / 私信

### 6. 用户主页

`UserRepository.user(username)` 已实现，缺 UI。

**展示**：头像、bio_cooked、统计（阅读时长 / 徽章 / 访问量）、
最近创建的话题（`/topics/created-by/{username}.json`）。

**入口**：帖子里点头像、帖子作者名。

### 7. MessageBus 长轮询

iOS 版有 `MessageBusService`，做实时未读与在线状态。当前是「进通知页才刷新」。

**协议**（Discourse 标准）：
```
POST /message-bus/{clientId}/poll
  body: {"channels":{"/notification/{userId}":-1}}
  → 长轮询，有消息立即返回，无消息 25s 超时
```

**改动点**：
- 新增 `data/remote/MessageBus.kt`，`clientId` 用随机 UUID 存 SessionStore
- 前台时启动协程轮询，`Lifecycle.State.RESUMED` 才跑
- 收到 `/notification/{id}` 就 `refreshSession()` 更新角标

**注意**：别在后台跑，费电且 Discourse 会限流。

---

## P2 — 体验优化

### 8. onebox / 投票 / 数学公式

- **onebox**：`<aside class="onebox">` 是链接预览卡片，可以抽 `title` / `description` / `thumbnail` 自己画
- **投票**：`<div class="poll">`，数据其实在 post 的 `polls` 字段里（需 `?include_raw` 或单独端点）
- **数学**：`<span class="math">` 是 KaTeX 输出，安卓侧要么接 WebView + KaTeX，要么用 jLaTeXMath

### 9. 离线阅读

`TopicListCache` 在 iOS 版是 SwiftData。安卓侧可以用 Room 存最近 N 个话题的正文。

### 10. 手势与动效

- 双击帖子点赞
- 列表项滑动操作（收藏 / 标记已读）
- 详情页顶部标题随滚动收起

---

## P3 — 微应用（Pajero）

**前置条件：服务端商店必须已开张。**

当前 `PajeroConfiguration.json` 的 `catalogURL=null`、`trustedKeys={}`，
包内只有 3 个 demo。移植前先确认 `GET /mini-app-api/apps` 有内容。

### 若要做，分五步

1. **JS 引擎**（~800 行）
   QuickJS-Android 或 J2V8。原语只有三个：`__miniSend` / `__miniSchedule` / `__miniCancelTimer`。
   `PajeroSDK.js` 本身是引擎探测的（`if (typeof globalThis.AbortController === "undefined")`），可复用。

2. **native renderer**（~700 行）
   JSON 描述 UI → Compose 控件。控件类型：
   `button / input / multiline / toggle / picker / menu / segmented / date / slider / stepper / progress / column / list / text`

3. **本地回环资源服务**（~400 行）
   `ServerSocket` 绑 127.0.0.1 随机端口，WebView 从那儿加载。
   需要复刻 iOS 的头：`Access-Control-Allow-Private-Network`、`Cross-Origin-Resource-Policy`。
   **风险**：Chrome PNA 预检在 WebView 里行为和桌面不一致，要实测。

4. **签名验签**（未知工作量）
   manifest 结构 `{keyID, payload, signature}`，`trustedKeys` 是公钥表。
   **尚未确认签名算法**（二进制里有 `ed25519` 字符串但可能是 OpenSSL 算法表）。
   开工第一件事：读 `discourse-mini-app` 插件源码确认格式。

5. **能力桥**（~600 行）
   蓝牙、摄像头录制、相册、定位、分账号 storage（带 revision 乐观锁）。
   每项都要重写 permission flow + 授权 UI。

### 判断标准

```bash
curl -sS 'https://linux.do/mini-app-api/apps' \
  -H 'Accept: application/json' \
  -H 'X-Requested-With: XMLHttpRequest' \
  -H 'Cookie: _t=<你的>' | python3 -m json.tool
```

- 返回 `{"apps":[]}` → 商店没开，不做
- 返回有内容的数组 → 按上面五步做，预计 2,500 行

---

## 已完成（本轮）

- **CSRF**：写操作带 `X-CSRF-Token`，403 刷新后单次重放，token 落加密盘。
  原来发帖 / 回复 / 点赞全 403 `Invalid Authenticity Token`。
- **站点地址参数化**：`avatarUrl` / `absolutize` 不再写死 `https://linux.do`，
  自建 Discourse 的头像与正文图片跟着「服务器地址」走（含反向代理子路径）。
- **深链**：`/t/{slug}/{id}/{post}` 真正打开详情页并滚到指定楼层，
  解析放在 `util/TopicLink.kt`，纯字符串实现可单测。
- **实体解码顺序**：数字实体先于 `&amp;` 解码，越界码点丢空而非抛异常；
  双重转义 `&amp;#1114112;` 不再崩列表页。
- **列表缓存**：`ConcurrentHashMap` 换成有上限的 LRU + 锁。
- **切站点**：`HttpClients.invalidate()` 接上，旧 client 不再残留。
- **单元测试**：四组 JVM 测试，`./gradlew :app:testDebugUnitTest`。

## 技术债

| 位置 | 问题 | 影响 |
|---|---|---|
| `ui/browse/ComposeShims.kt` | 只是 `collectAsState` 的包装，纯噪音 | 可删，直接 import |
| `ui/MainScreen.kt:176` | `simpleFactory` 定义在 UI 文件里 | 应移到 `ui/common/` |
| ~~`HttpClients`~~ | ~~`invalidate()` 没人调用~~ | 已修：`changeServer` 走 `container.onSiteChanged()` |
| `Doh.kt` | 已挂载，仅处理 `linux.do` / `*.linux.do` | 双路都失败时回落系统 DNS；端点依赖第三方域名 |
| `util/Html.isCjkDominant()` | 没人用 | 删除或接入排版判断 |
| ~~全项目~~ | ~~零单元测试~~ | 已补：Html / TopicLink / SiteUrl / CSRF / 分类解析五组 |
| `DraftStore.allKeys()` | 没人用 | 草稿列表 UI 才需要 |

---

## 已知不做

- **App Attest / DeviceCheck / mTLS**：绑定 Apple 生态，安卓无对应能力。
  官方 App 用它做设备风控，第三方走标准 cookie 更透明。
- **聊天（Chat 插件）**：`/chat/api/*` 是一整套独立子系统，工作量等同再写半个 App。
  需要时单独开分支。
