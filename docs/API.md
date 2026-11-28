# Discourse API 参考

只列本项目实际用到 / 计划用到的端点。
完整文档见 `https://docs.discourse.org`。

所有请求都带：

```
Accept: application/json
X-Requested-With: XMLHttpRequest
Discourse-Logged-In: true
Cookie: _t=...
```

写操作额外带 `Origin` / `Referer`。

---

## 会话

### `GET /session/current.json`

当前登录状态。未登录返回 403。

```json
{
  "current_user": {
    "id": 115923,
    "username": "Discourse",
    "name": null,
    "avatar_template": "/user_avatar/.../{size}/580155_2.png",
    "trust_level": 4,
    "admin": true
  },
  "all_unread_notifications_count": 3,
  "unread_high_priority_notifications": 0,
  "unseen_reviewable_count": 0,
  "grouped_unread_notifications": {"1": 2}
}
```

**代码**：`UserRepository.refreshSession()`

### `POST /session`

密码登录。form 参数：

```
login=<用户名或邮箱>
password=<密码>
second_factor_method=1        # 可选：1=TOTP，2=备用码
second_factor_token=123456    # 2FA 账号必填
```

失败返回 `{"error": "..."}` 或 `{"reason": "...", "wait_seconds": 5}`。

**两步验证**：密码正确但缺验证码时返回
`{"error":"...","reason":"invalid_second_factor","totp_enabled":true,"backup_enabled":true,"security_key_enabled":false}`；
带上 `second_factor_method` + `second_factor_token` **重新 POST `/session`** 即可。
安全密钥 / Passkey 走网页登录。

**人机验证**：站点开启 hCaptcha / reCaptcha 时会返回「人机验证失败」，
原生请求无法代过验证码，需要改走网页登录（App 会给出入口）。

**代码**：`UserRepository.login()` / `loginWithSecondFactor()`

### 邮箱一次性登录代码

```
GET  /session/hp.json              → {"value":"...","challenge":"..."}
POST /session/login-code           email, signup=false,
                                   password_confirmation=<value>,
                                   challenge=<challenge 反转>
POST /session/login-code/verify    email, code
                                   （2FA 账号再带 second_factor_method / second_factor_token）
```

验证通过后服务端种 cookie，再 `GET /session/current.json` 确认登录态。
发信接口在开启 hCaptcha 的站点会被拦截，App 检测到「人机验证失败」会引导到网页登录。

**代码**：`UserRepository.requestLoginCode()` / `verifyLoginCode()`

### `POST /session/forgot_password`

form：`login=<用户名或邮箱>`。只发重置邮件，响应不暴露账号是否存在。

**代码**：`UserRepository.forgotPassword()`

### 第三方 / 通行密钥登录

Discourse 的 OAuth（Google / GitHub / Discord / Apple）与 Passkey 都在站点登录页完成。
App 用 WebView 打开 `/auth/{provider}` 或 `/login`，登录成功后把 cookie（含 `_t`）
同步进 OkHttp 的 cookie jar，再走 `/session/current.json`。

**代码**：`WebLoginDialog` + `UserRepository.completeWebLogin()`

### `DELETE /session/{username}`

登出。忘掉这一步也能靠清 cookie 达到效果，但服务端 session 会残留。

---

## 站点

### `GET /site/basic-info.json`

轻量站点信息，不需要登录。

```json
{
  "title": "LINUX DO",
  "description": "...",
  "logo_url": "...",
  "logo_small_url": "...",
  "favicon_url": "...",
  "login_required": false,
  "locale": "zh_CN"
}
```

**代码**：`UserRepository.siteBasicInfo()`

### `GET /site.json`

重量级，返回全部站点配置（几 MB）。包含：
`filters`（可用列表类型）、`periods`、`post_action_types`、`notification_types`、
`groups`、`user_themes`、`user_color_schemes`。

**不需要拉这个**。`filters` 和 `periods` 已硬编码在 `UserRepository.ListFilter` /
`UserRepository.Period` 里。需要动态化才用它。

### `GET /about.json`

站点统计。用不上，仅供参考。

---

## 话题列表

### `GET /latest.json?page=0&per_page=30`

其他列表同理：`/top.json`、`/new.json`、`/unread.json`、`/hot.json`、`/bookmarks.json`

分类维度：`/c/{slug}/{id}/l/{filter}.json`（只给 id 也可以，服务端 301 到规范地址）

分类列表：`/categories.json?include_subcategories=true`
（顶级分类的 `subcategory_list` 里会带完整子分类）

周期（仅 top）：`/top.json?period=weekly`，可选
`daily` / `weekly` / `monthly` / `quarterly` / `yearly` / `all`

```json
{
  "users": [{"id": 2770, "username": "awesomerobot", ...}],
  "primary_groups": [...],
  "flair_groups": [...],
  "topic_list": {
    "can_create_topic": false,
    "per_page": 30,
    "more_topics_url": "/latest?no_definitions=true&page=1",
    "top_tags": [...],
    "topics": [
      {
        "id": 417307,
        "title": "Try the experimental mobile footer navigation!",
        "fancy_title": "...",
        "slug": "try-the-experimental-mobile-footer-navigation",
        "posts_count": 31,
        "reply_count": 19,
        "highest_post_number": 31,
        "created_at": "2026-11-16T15:21:17.459Z",
        "bumped_at": "2026-11-24T23:38:07.053Z",
        "pinned": false,
        "closed": false,
        "excerpt": "We're introducing...",
        "thumbnails": [{"url": "...", "max_width": 600, "width": 275, "height": 600}],
        "tags": [{"id": 35, "name": "mobile", "slug": "mobile"}],
        "views": 424,
        "like_count": 64,
        "category_id": 67,
        "last_poster_username": "sk-or-v1-contents",
        "posters": [
          {"user_id": 2770, "description": "Original Poster", "extras": null},
          {"user_id": 190268, "description": "Most Recent Poster", "extras": "latest"}
        ]
      }
    ]
  }
}
```

**关键字段**：
- `more_topics_url: null` → 到底了（本项目用它判断 `endReached`）
- `posters[].description` 里 `"Original Poster"` / `"Most Recent Poster"` 用于标记
- `thumbnails` 按尺寸降序，取第一个是最清晰的

**代码**：`UserRepository.topicList()`

### `GET /categories.json`

```json
{
  "category_list": {
    "can_create_topic": false,
    "categories": [
      {
        "id": 67,
        "name": "Feature",
        "slug": "feature",
        "color": "0088CC",
        "text_color": "FFFFFF",
        "topic_count": 1234,
        "parent_category_id": 208,
        "subcategory_ids": [3, 2, 1],
        "read_restricted": false
      }
    ]
  }
}
```

**注意**：`color` 是不带 `#` 的 6 位 hex，用 `parseHexColor()` 解析。
`parent_category_id` 为 null 是顶级分类。

**代码**：`UserRepository.categories()`

---

## 话题详情

### `GET /t/{id}.json`

```json
{
  "id": 417307,
  "title": "...",
  "posts_count": 31,
  "highest_post_number": 31,
  "reply_count": 19,
  "views": 424,
  "like_count": 64,
  "category_id": 67,
  "tags": [{"id": 35, "name": "mobile"}],
  "bookmarked": false,
  "details": {
    "can_edit": false,
    "notification_level": 1,
    "created_by": {...},
    "participants": [...]
  },
  "post_stream": {
    "posts": [ /* 前 20 楼 */ ],
    "stream": [2065127, 2066145, ...]  /* 全部 post id */
  }
}
```

### 楼层对象

```json
{
  "id": 2065127,
  "username": "awesomerobot",
  "name": "Kris",
  "avatar_template": "...",
  "created_at": "2026-11-16T15:21:17.459Z",
  "cooked": "<p>We're introducing...</p>",
  "raw": "We're introducing...",
  "post_number": 1,
  "post_type": 1,
  "reply_to_post_number": null,
  "reads": 113,
  "version": 2,
  "can_edit": false,
  "user_id": 2770,
  "actions_summary": [{"id": 2, "count": 20}],
  "trust_level": 3,
  "user_title": "team",
  "reply_to_user": {"id": 207575, "username": "kmm1", "name": null},
  "accepted_answer": false
}
```

**`actions_summary` id 含义**：
- `2` = 点赞（`count` 是数量）
- `4` = 收藏
- `6` = 举报
- `8` = 编辑

**代码**：`UserRepository.topic()`

### `GET /t/{id}.json?post_number=N`

加载第 N 楼附近的帖子。用于无限滚动。

**注意**：返回的 `post_stream.posts` 可能包含**已经加载过**的楼层
（Discourse 会返回 N 前后各若干楼）。必须用 post id 去重。

**代码**：`UserRepository.postsNear()`

### `POST /t/{id}/timings`

标记已读。form：

```
timings[0][post_number]=5
timings[0][msecs]=1000
```

不调也不影响功能，只影响服务端记录阅读进度。

**代码**：`UserRepository.markTopicRead()`

---

## 发帖 / 回复

### `POST /posts`

发新帖：

```
title=标题
raw=Markdown 正文
category=67
```

回复：

```
topic_id=417307
raw=回复内容
reply_to_post_number=5      (可选)
```

返回：

```json
{"id": 2065128, "topic_id": 417307, "post_number": 2, ...}
```

**发新帖时** `topic_id` 是新建的；**回复时** `topic_id` 是传入的。

**代码**：`UserRepository.createTopic()` / `reply()`

### `PUT /posts/{id}`

编辑。form：`post[raw]=新内容`

**代码**：`UserRepository.editPost()`

---

## 互动

### `POST /post_actions`

点赞。form：

```
id=2065127
post_action_type_id=2
```

### `DELETE /post_actions/{postId}?post_action_type_id=2`

取消点赞。

返回里 `actions_summary` 含最新 count。

**代码**：`UserRepository.toggleLike()`

### `POST /bookmarks`

收藏话题：

```
bookmarkable_id=417307
bookmarkable_type=Topic
```

### `DELETE /bookmarks/{topicId}`

取消收藏。

**代码**：`UserRepository.toggleBookmark()`

---

## 通知

### `GET /notifications.json?offset=0`

```json
{
  "notifications": [
    {
      "id": 12345,
      "user_id": 115923,
      "notification_type": 2,
      "read": false,
      "created_at": "2026-11-24T23:38:07.053Z",
      "post_number": 31,
      "topic_id": 417307,
      "slug": "...",
      "fancy_title": "...",
      "data": {
        "topic_title": "...",
        "display_username": "awesomerobot",
        "original_username": "...",
        "count": 1
      }
    }
  ],
  "total_rows_notifications": 50,
  "seen_notification_id": 12340,
  "load_more_notifications": "/notifications.json?offset=30"
}
```

**`notification_type` 常见值**（详见 `NotificationsScreen.kt` 的 `typeLabel()`）：

| id | 含义 |
|---|---|
| 1 | 提到了你 |
| 2 | 回复了你 |
| 3 | 引用了你 |
| 5 | 赞了你 |
| 6, 7 | 私信 |
| 12 | 获得徽章 |
| 19 | 赞（合并） |
| 25 | 回应 |
| 29-33, 40 | 聊天 |
| 800-802 | 关注动态 |

### `PUT /notifications/mark-read`

全部已读：`dismiss=true`

**代码**：`UserRepository.markAllNotificationsRead()`

---

## 搜索

### `GET /search.json?q={term}&page=1&include_blurbs=true`

```json
{
  "posts": [
    {
      "id": 2065127,
      "username": "awesomerobot",
      "avatar_template": "...",
      "created_at": "...",
      "like_count": 20,
      "blurb": "...<span class=\"search-highlight\">keyword</span>...",
      "post_number": 1,
      "topic_id": 417307
    }
  ],
  "topics": [ /* 命中的话题 */ ],
  "users": [ /* 命中的用户 */ ],
  "categories": [ /* 命中的分类 */ ],
  "tags": [ /* 命中的标签 */ ],
  "grouped_search_result": {
    "term": "keyword",
    "search_log_id": 5341506,
    "more_full_page_results": true,
    "can_create_topic": false,
    "error": null
  }
}
```

**`blurb`** 含 `<span class="search-highlight">` 高亮标记。
用 `Html.toPlain()` 去掉标签后高亮就没了；要保留得单独解析。

**注意**：`grouped_search_result.error` 非 null 时说明搜索被拒
（比如关键词太短、被限流）。

**代码**：`UserRepository.search()`

---

## 用户

### `GET /u/{username}.json`

```json
{
  "user": {
    "id": 115923,
    "username": "Discourse",
    "name": null,
    "avatar_template": "...",
    "trust_level": 4,
    "admin": true,
    "bio_excerpt": "...",
    "bio_cooked": "<p>...</p>",
    "created_at": "...",
    "last_seen_at": "...",
    "time_read": 35248,
    "badge_count": 31,
    "profile_view_count": 24362,
    "can_send_private_message_to_user": false,
    "groups": [{"id": 73, "name": "theme_authors"}]
  },
  "user_badges": [{"id": 492869, "badge_id": 4, "granted_at": "..."}],
  "badges": [{"id": 4, "name": "Leader", "icon": "user", "grant_count": 26}]
}
```

**代码**：`UserRepository.user()`

### `GET /topics/created-by/{username}.json?page=0`

该用户创建的话题，格式同 `latest.json`。

**代码**：`UserRepository.userTopics()`

---

## 上传

### `POST /uploads.json`

multipart：

```
file=@/path/to/image.jpg
type=composer
```

返回：

```json
{
  "url": "/uploads/default/original/4X/a/b/c/abc123.jpg",
  "short_url": "upload://abc123.jpg",
  "short_path": "/uploads/short-url/abc123.jpg",
  "original_filename": "image.jpg",
  "width": 1920,
  "height": 1080,
  "human_filesize": "254 KB"
}
```

**正文里用 `short_url`**：

```markdown
![image|690x248](upload://abc123.jpg)
```

服务端渲染 `cooked` 时会展开成完整 URL。

**代码**：`DiscourseApi.uploadFile()`（UI 未接，见 ROADMAP P0-3）

---

## 私信

### `GET /topics/private-messages/{username}.json?page=0`

个人私信列表。格式同 `latest.json`。

### 新建私信

`POST /posts` 带额外参数：

```
archetype=private_message
target_recipients=username1,username2
title=私信标题
raw=正文
```

**注意**：私信的 `archetype` 是 `private_message`，
但 `/t/{id}.json` 端点相同，详情页可以复用。

### `GET /private-message-topic-tracking-state.json`

未读私信计数。

---

## 未实现但可能需要的

| 端点 | 用途 |
|---|---|
| `GET /message-bus/{clientId}/poll` | 长轮询实时更新 |
| `GET /mini-app-api/apps` | 微应用商店列表 |
| `GET /review/queue.json` | 审核队列（仅版主） |
| `GET /chat/api/channels.json` | 聊天频道（插件） |
| `GET /tags.json` | 全部标签 |
| `GET /groups.json` | 全部群组 |
| `POST /uploads/complete-multipart.json` | 大文件分片上传 |

---

## 限流

Discourse 默认：

- 普通用户：每分钟 60 次请求
- 登录：每次失败后递增等待（`wait_seconds` 字段）
- 发帖：新用户有 20 秒冷却

`ApiException.Kind.RATE_LIMIT` 对应 HTTP 429。
UI 侧应提示「操作太频繁」而不是「未知错误」（已在 `friendlyMessage()` 处理）。
