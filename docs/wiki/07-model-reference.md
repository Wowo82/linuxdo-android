# 07 · 数据模型参考

模型全部位于 [Models.kt](../../app/src/main/java/org/linuxdo/android/data/model/Models.kt)
（`TagOrString` 及其序列化器在 [TagOrString.kt](../../app/src/main/java/org/linuxdo/android/data/model/TagOrString.kt)）。

## 1. 约定

1. 所有模型标注 `@Serializable`，字段使用 `@SerialName` 映射下划线 JSON 名；
2. 除自定义序列化的 `TagOrString` 外，**每个字段都有默认值**——Discourse 不同版本 / 插件会增删字段，缺默认值会直接反序列化失败；
3. 反序列化配置见 06 §1.2：`ignoreUnknownKeys = true`、`coerceInputValues = true`；
4. 模型只包含只读派生属性（`val xxx get() = …`），不放业务逻辑；
5. 服务端字段的语义以 `docs/API.md` 为准，本文只列代码中的字段与类型。

---

## 2. 会话与站点

### 2.1 `SessionResponse`

来源：`GET /session/current.json`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `currentUser` | `current_user` | `User?` | 未登录时为 null |
| `allUnreadNotificationsCount` | `all_unread_notifications_count` | `Int` | 底部导航角标来源 |
| `unreadHighPriority` | `unread_high_priority_notifications` | `Int` | 高优先级未读 |
| `unseenReviewable` | `unseen_reviewable_count` | `Int` | 审核队列未看数（未使用） |
| `grouped` | `grouped_unread_notifications` | `Map<String, Int>` | 分组未读（未使用） |

### 2.2 `SiteBasicInfo`

来源：`GET /site/basic-info.json`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `title` | `title` | `String` | 默认 `"Discourse"`，实际展示站点名 |
| `description` | `description` | `String?` | 站点描述 |
| `logoUrl` | `logo_url` | `String?` | 大 logo |
| `logoSmallUrl` | `logo_small_url` | `String?` | 小 logo，优先作为站点图标 |
| `mobileLogoUrl` | `mobile_logo_url` | `String?` | 移动端 logo |
| `faviconUrl` | `favicon_url` | `String?` | favicon，第三优先 |
| `appleTouchIconUrl` | `apple_touch_icon_url` | `String?` | 第二优先 |
| `loginRequired` | `login_required` | `Boolean` | 站点是否强制登录 |
| `locale` | `locale` | `String` | 默认 `"en"` |

图标选择顺序（`UserRepository.siteBasicInfo()` 与 `MainViewModel.boot()`）：
`logoSmallUrl` → `appleTouchIconUrl` → `faviconUrl`。

---

## 3. 用户

### 3.1 `User`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 用户 id |
| `username` | `username` | `String` | 唯一用户名 |
| `name` | `name` | `String?` | 显示名，可能为空 |
| `avatarTemplate` | `avatar_template` | `String?` | 含 `{size}` 的头像模板 |
| `trustLevel` | `trust_level` | `Int` | TL0–TL4 |
| `admin` | `admin` | `Boolean` | 管理员 |
| `moderator` | `moderator` | `Boolean` | 版主 |
| `title` | `title` | `String?` | 用户头衔 |
| `primaryGroupName` | `primary_group_name` | `String?` | 主群组名 |
| `flairName` | `flair_name` | `String?` | 装饰名 |
| `flairUrl` | `flair_url` | `String?` | 装饰图标 |
| `flairBgColor` | `flair_bg_color` | `String?` | 装饰背景色 |
| `flairColor` | `flair_color` | `String?` | 装饰文字色 |
| `flairGroupId` | `flair_group_id` | `Long?` | 装饰群组 id |
| `bioExcerpt` | `bio_excerpt` | `String?` | 简介摘要（仅 `/u/{name}.json`） |
| `bioCooked` | `bio_cooked` | `String?` | 简介 HTML |
| `lastSeenAt` | `last_seen_at` | `String?` | ISO8601 |
| `createdAt` | `created_at` | `String?` | 注册时间 |
| `lastPostedAt` | `last_posted_at` | `String?` | 最后发帖时间 |
| `timeRead` | `time_read` | `Int?` | 阅读秒数 |
| `badgeCount` | `badge_count` | `Int?` | 徽章数 |
| `profileViewCount` | `profile_view_count` | `Int?` | 主页访问量 |
| `canSendPm` | `can_send_private_message_to_user` | `Boolean` | 是否可给该用户发私信 |
| `canSendPmToUser` | `can_send_private_messages` | `Boolean` | 是否可发私信（当前用户维度） |
| `groups` | `groups` | `List<Group>` | 所属群组 |
| `userFields` | `user_fields` | `Map<String, String?>` | 自定义用户字段 |

派生属性：

| 属性 | 计算 |
|---|---|
| `displayName` | `name` 非空白则用 `name`，否则 `username` |
| `mainFlair` | `flairName ?: primaryGroupName` |

### 3.2 `Group`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `id` | `id` | `Long` |
| `name` | `name` | `String` |
| `fullName` | `full_name` | `String?` |
| `flairUrl` | `flair_url` | `String?` |
| `flairBgColor` | `flair_bg_color` | `String?` |
| `flairColor` | `flair_color` | `String?` |
| `userCount` | `user_count` | `Int?` |
| `automatic` | `automatic` | `Boolean` |

### 3.3 `UserDetailResponse`

来源：`GET /u/{username}.json`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `user` | `user` | `User` | 用户主体 |
| `badges` | `user_badges` | `List<UserBadge>` | 用户获得的徽章 |
| `badgeInfo` | `badges` | `List<Badge>` | 徽章定义 |

### 3.4 `UserBadge`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `id` | `id` | `Long` |
| `badgeId` | `badge_id` | `Long` |
| `grantedAt` | `granted_at` | `String?` |
| `count` | `count` | `Int` |

### 3.5 `Badge`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `id` | `id` | `Long` |
| `name` | `name` | `String` |
| `description` | `description` | `String?` |
| `icon` | `icon` | `String?` |
| `imageUrl` | `image_url` | `String?` |
| `grantCount` | `grant_count` | `Int` |
| `badgeTypeId` | `badge_type_id` | `Int` |

---

## 4. 话题列表

### 4.1 `TopicListResponse`

来源：`GET /latest.json` 等

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `users` | `users` | `List<User>` | 列表中出现的用户（用于补全头像） |
| `primaryGroups` | `primary_groups` | `List<Group>` | 主群组 |
| `flairGroups` | `flair_groups` | `List<Group>` | 装饰群组 |
| `topicList` | `topic_list` | `TopicList` | 话题本体 |

### 4.2 `TopicList`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `canCreateTopic` | `can_create_topic` | `Boolean` | 当前用户能否发帖 |
| `perPage` | `per_page` | `Int` | 默认 30 |
| `moreTopicsUrl` | `more_topics_url` | `String?` | **为 null 表示已到最后一页** |
| `topTags` | `top_tags` | `List<Tag>` | 热门标签 |
| `topics` | `topics` | `List<Topic>` | 话题数组 |
| `categoryList` | `category_list` | `CategoryList?` | 仅 categories 路由返回 |

### 4.3 `Tag`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `id` | `id` | `Long` |
| `name` | `name` | `String` |
| `slug` | `slug` | `String` |

### 4.4 `Topic`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 话题 id |
| `title` | `title` | `String` | 纯标题（可能含 HTML 实体） |
| `fancyTitle` | `fancy_title` | `String?` | 含 HTML 标签的标题 |
| `slug` | `slug` | `String` | URL slug |
| `postsCount` | `posts_count` | `Int` | 总楼层数 |
| `replyCount` | `reply_count` | `Int` | 回复数 |
| `highestPostNumber` | `highest_post_number` | `Int` | 最高楼层号 |
| `imageUrl` | `image_url` | `String?` | 原图 |
| `createdAt` | `created_at` | `String?` | 创建时间 |
| `lastPostedAt` | `last_posted_at` | `String?` | 最后发帖时间 |
| `bumpedAt` | `bumped_at` | `String?` | 最后顶起时间（列表展示优先） |
| `pinned` | `pinned` | `Boolean` | 置顶 |
| `pinnedGlobally` | `pinned_globally` | `Boolean` | 全局置顶 |
| `closed` | `closed` | `Boolean` | 已关闭 |
| `archived` | `archived` | `Boolean` | 已归档 |
| `visible` | `visible` | `Boolean` | 可见 |
| `unseen` | `unseen` | `Boolean` | 未看过 |
| `liked` | `liked` | `Boolean?` | 当前用户是否赞过首楼 |
| `bookmarked` | `bookmarked` | `Boolean?` | 当前用户是否收藏 |
| `excerpt` | `excerpt` | `String?` | 摘要（HTML） |
| `thumbnails` | `thumbnails` | `List<Thumbnail>?` | 缩略图 |
| `tags` | `tags` | `List<TagOrString>` | 标签，两种 JSON 形态 |
| `views` | `views` | `Int` | 浏览量 |
| `likeCount` | `like_count` | `Int` | 点赞数 |
| `hasAcceptedAnswer` | `has_accepted_answer` | `Boolean` | 是否有已采纳答案 |
| `voteCount` | `vote_count` | `Int` | 投票数（插件） |
| `canVote` | `can_vote` | `Boolean` | 能否投票 |
| `categoryId` | `category_id` | `Long?` | 分类 id |
| `lastPosterUsername` | `last_poster_username` | `String?` | 最后回复人 |
| `posters` | `posters` | `List<Poster>` | 参与者摘要 |
| `archetype` | `archetype` | `String` | `regular` / `private_message` |
| `hasSummary` | `has_summary` | `Boolean` | 是否有摘要（AI 摘要等） |
| `unread` | `unread` | `Int` | 未读楼层数 |
| `newPosts` | `new_posts` | `Int` | 新帖数 |
| `lastReadPostNumber` | `last_read_post_number` | `Int?` | 最后已读楼层 |
| `notificationLevel` | `notification_level` | `Int?` | 通知级别 |
| `unreadPosts` | `unread_posts` | `Int` | 未读帖数 |

派生属性：

| 属性 | 计算 |
|---|---|
| `isUnread` | `unread > 0 \|\| newPosts > 0` |
| `plainTitle` | `(fancyTitle ?: title)` 去掉所有 `<...>` 标签 |
| `bestThumb` | `thumbnails.firstOrNull()?.url ?: imageUrl` |

### 4.5 `Thumbnail`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `url` | `url` | `String?` |
| `width` | `width` | `Int?` |
| `height` | `height` | `Int?` |
| `maxWidth` | `max_width` | `Int?` |
| `maxHeight` | `max_height` | `Int?` |

### 4.6 `Poster`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `userId` | `user_id` | `Long` | 用户 id |
| `description` | `description` | `String` | 英文描述：`Original Poster` / `Frequent Poster` / `Most Recent Poster` |
| `extras` | `extras` | `String?` | 附加信息 |

派生属性：`isOp`（含 `Original`）、`isLatest`（含 `Recent`）、`isFrequent`（含 `Frequent`），均忽略大小写。

### 4.7 `CategoryList`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `canCreateTopic` | `can_create_topic` | `Boolean` |
| `canCreateCategory` | `can_create_category` | `Boolean` |
| `categories` | `categories` | `List<Category>` |

### 4.8 `Category`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 分类 id |
| `name` | `name` | `String` | 名称 |
| `slug` | `slug` | `String` | URL slug |
| `color` | `color` | `String` | 6 位 hex，**不带 `#`** |
| `textColor` | `text_color` | `String` | 6 位 hex |
| `icon` | `icon` | `String?` | 图标名 |
| `emoji` | `emoji` | `String?` | emoji |
| `styleType` | `style_type` | `String?` | 样式类型 |
| `topicCount` | `topic_count` | `Int` | 话题数 |
| `postCount` | `post_count` | `Int` | 帖子数 |
| `position` | `position` | `Int` | 排序 |
| `descriptionText` | `description_text` | `String?` | 纯文本描述 |
| `descriptionExcerpt` | `description_excerpt` | `String?` | 描述摘要 |
| `parentCategoryId` | `parent_category_id` | `Long?` | null 表示顶级分类 |
| `readRestricted` | `read_restricted` | `Boolean` | 阅读受限 |
| `hasChildren` | `has_children` | `Boolean?` | 是否有子分类 |
| `subcategoryIds` | `subcategory_ids` | `List<Long>` | 子分类 id |
| `uploadedLogo` | `uploaded_logo` | `MediaObject?` | 上传的 logo |
| `colorScheme` | `colorScheme` | `String?` | 注意：**字段名就是 camelCase**，没有 `@SerialName` |

### 4.9 `MediaObject`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `url` | `url` | `String?` |
| `width` | `width` | `Int?` |
| `height` | `height` | `Int?` |

---

## 5. 话题详情

### 5.1 `TopicDetail`

来源：`GET /t/{id}.json`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 话题 id |
| `title` | `title` | `String` | 标题 |
| `fancyTitle` | `fancy_title` | `String?` | 含标签标题 |
| `slug` | `slug` | `String` | slug |
| `postsCount` | `posts_count` | `Int` | 总楼层 |
| `highestPostNumber` | `highest_post_number` | `Int` | 最高楼层号 |
| `createdAt` | `created_at` | `String?` | 创建时间 |
| `lastPostedAt` | `last_posted_at` | `String?` | 最后发帖 |
| `replyCount` | `reply_count` | `Int` | 回复数 |
| `likeCount` | `like_count` | `Int` | 点赞数 |
| `views` | `views` | `Int` | 浏览量 |
| `wordCount` | `word_count` | `Int` | 字数 |
| `participantCount` | `participant_count` | `Int` | 参与人数 |
| `closed` | `closed` | `Boolean` | 已关闭 |
| `archived` | `archived` | `Boolean` | 已归档 |
| `pinned` | `pinned` | `Boolean` | 置顶 |
| `categoryId` | `category_id` | `Long?` | 分类 |
| `userId` | `user_id` | `Long` | 作者 id |
| `tags` | `tags` | `List<TagOrString>` | 标签 |
| `bookmarked` | `bookmarked` | `Boolean` | 当前用户是否收藏 |
| `canEdit` | `can_edit` | `Boolean` | 能否编辑 |
| `details` | `details` | `TopicDetails` | 详情元数据 |
| `postStream` | `post_stream` | `PostStream` | 楼层流 |
| `draft` | `draft` | `Draft?` | 服务端草稿 |
| `acceptedAnswer` | `accepted_answer` | `Boolean` | 该层是否被采纳 |
| `hasAcceptedAnswer` | `has_accepted_answer` | `Boolean` | 是否有采纳答案 |
| `canVote` | `can_vote` | `Boolean` | 能否投票 |
| `voteCount` | `vote_count` | `Int` | 票数 |
| `userVoted` | `user_voted` | `Boolean?` | 当前用户是否投过 |
| `createAsSharedIssue` | `create_as_shared_issue` | `Boolean?` | 插件字段 |

派生属性：`plainTitle` = `(fancyTitle ?: title)` 去标签。

### 5.2 `TopicDetails`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `canEdit` | `can_edit` | `Boolean` | 能否编辑 |
| `notificationLevel` | `notification_level` | `Int` | 通知级别，默认 1 |
| `createdBy` | `created_by` | `User?` | 创建者 |
| `participants` | `participants` | `List<Participant>` | 参与者 |
| `links` | `links` | `List<OneboxLink>` | 外链统计 |

### 5.3 `Participant`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `id` | `id` | `Long` |
| `username` | `username` | `String` |
| `name` | `name` | `String?` |
| `avatarTemplate` | `avatar_template` | `String?` |
| `postCount` | `post_count` | `Int` |
| `primaryGroupName` | `primary_group_name` | `String?` |
| `flairName` | `flair_name` | `String?` |
| `flairUrl` | `flair_url` | `String?` |
| `flairColor` | `flair_color` | `String?` |
| `flairBgColor` | `flair_bg_color` | `String?` |
| `flairGroupId` | `flair_group_id` | `Long?` |
| `trustLevel` | `trust_level` | `Int` |

### 5.4 `OneboxLink`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `url` | `url` | `String` | 链接 |
| `title` | `title` | `String?` | 标题 |
| `clicks` | `clicks` | `Int` | 点击次数 |

同时用于 `Post.link_counts`。

### 5.5 `PostStream`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `posts` | `posts` | `List<Post>` | 本次返回的楼层（首屏约 20 楼） |
| `stream` | `stream` | `List<Long>` | 全部 post id（稀疏，可能含已删除项） |

### 5.6 `Post`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 楼层 id |
| `name` | `name` | `String?` | 作者显示名 |
| `username` | `username` | `String` | 作者用户名 |
| `avatarTemplate` | `avatar_template` | `String?` | 头像模板 |
| `createdAt` | `created_at` | `String?` | 发布时间 |
| `updatedAt` | `updated_at` | `String?` | 编辑时间 |
| `cooked` | `cooked` | `String` | 服务端渲染 HTML |
| `raw` | `raw` | `String?` | 原始 Markdown（`include_raw=true` 时） |
| `postNumber` | `post_number` | `Int` | 楼层号（从 1 开始） |
| `postType` | `post_type` | `Int` | 1 普通，3 小动作（如「xxx 关闭了话题」） |
| `replyToPostNumber` | `reply_to_post_number` | `Int?` | 回复的楼层号 |
| `replyCount` | `reply_count` | `Int` | 该楼被回复数 |
| `quoteCount` | `quote_count` | `Int` | 引用数 |
| `incomingLinkCount` | `incoming_link_count` | `Int` | 入链数 |
| `reads` | `reads` | `Int` | 阅读数 |
| `version` | `version` | `Int` | 编辑版本，>1 显示「已编辑」 |
| `canEdit` | `can_edit` | `Boolean` | 能否编辑 |
| `canDelete` | `can_delete` | `Boolean` | 能否删除 |
| `canSeeHiddenPost` | `can_see_hidden_post` | `Boolean` | 能否看隐藏帖 |
| `canWiki` | `can_wiki` | `Boolean` | 能否设为 wiki |
| `userId` | `user_id` | `Long` | 作者 id |
| `hidden` | `hidden` | `Boolean` | 是否隐藏 |
| `deletedAt` | `deleted_at` | `String?` | 删除时间 |
| `userDeleted` | `user_deleted` | `Boolean` | 作者已删 |
| `editReason` | `edit_reason` | `String?` | 编辑原因 |
| `canViewEditHistory` | `can_view_edit_history` | `Boolean` | 能否看编辑历史 |
| `wiki` | `wiki` | `Boolean` | 是否 wiki 帖 |
| `postUrl` | `post_url` | `String?` | 楼层链接 |
| `displayUsername` | `display_username` | `String?` | 展示用户名 |
| `primaryGroupName` | `primary_group_name` | `String?` | 主群组 |
| `flairName` | `flair_name` | `String?` | 装饰名 |
| `flairUrl` | `flair_url` | `String?` | 装饰图标 |
| `flairBgColor` | `flair_bg_color` | `String?` | 装饰背景色 |
| `flairColor` | `flair_color` | `String?` | 装饰文字色 |
| `flairGroupId` | `flair_group_id` | `Long?` | 装饰群组 id |
| `trustLevel` | `trust_level` | `Int` | 作者 TL |
| `userTitle` | `user_title` | `String?` | 用户头衔 |
| `titleIsGroup` | `title_is_group` | `Boolean` | 头衔是否来自群组 |
| `actionsSummary` | `actions_summary` | `List<ActionSummary>` | 互动摘要 |
| `moderator` | `moderator` | `Boolean` | 版主 |
| `admin` | `admin` | `Boolean` | 管理员 |
| `staff` | `staff` | `Boolean` | 官方人员 |
| `acceptedAnswer` | `accepted_answer` | `Boolean` | 被采纳答案 |
| `canAcceptAnswer` | `can_accept_answer` | `Boolean` | 能否采纳 |
| `canUnacceptAnswer` | `can_unaccept_answer` | `Boolean` | 能否取消采纳 |
| `replyToUser` | `reply_to_user` | `ReplyUser?` | 回复对象 |
| `linkCounts` | `link_counts` | `List<OneboxLink>` | 外链统计 |
| `yours` | `yours` | `Boolean` | 是否本人发布 |
| `topicId` | `topic_id` | `Long` | 所属话题 |
| `topicSlug` | `topic_slug` | `String?` | 话题 slug |

派生属性：

| 属性 | 计算 |
|---|---|
| `displayName` | `name` 非空白则用 `name`，否则 `username` |
| `likeCount` | `actionsSummary.firstOrNull { it.id == 2 }?.count ?: 0` |
| `liked` | `actionsSummary.firstOrNull { it.id == 2 }?.acted ?: false` |
| `isOp` | `postNumber == 1` |
| `isSmallAction` | `postType == 3` |

### 5.7 `ReplyUser`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `id` | `id` | `Long` |
| `username` | `username` | `String` |
| `name` | `name` | `String?` |
| `avatarTemplate` | `avatar_template` | `String?` |

### 5.8 `ActionSummary`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Int` | `2` = 点赞，`4` = 收藏，`6` = 举报，`8` = 编辑 |
| `count` | `count` | `Int` | 数量 |
| `hidden` | `hidden` | `Boolean?` | 是否隐藏 |
| `canAct` | `can_act` | `Boolean?` | 当前用户能否操作 |
| `acted` | `acted` | `Boolean?` | 当前用户是否已操作 |

### 5.9 `Draft`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `sequence` | `sequence` | `Long` |
| `reply` | `reply` | `String?` |
| `draftSequence` | `draft_sequence` | `Long?` |

服务端草稿模型，当前 UI 未使用。

---

## 6. 通知

### 6.1 `NotificationsResponse`

来源：`GET /notifications.json?offset=0`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `notifications` | `notifications` | `List<Notification>` | 当前页通知 |
| `totalRows` | `total_rows_notifications` | `Int` | 总条数 |
| `seenNotificationId` | `seen_notification_id` | `Long` | 已读游标 |
| `loadMore` | `load_more_notifications` | `String?` | 下一页 URL（未使用） |

### 6.2 `Notification`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 通知 id |
| `userId` | `user_id` | `Long` | 接收者 |
| `notificationType` | `notification_type` | `Int` | 类型 id，映射见 08 §6 |
| `read` | `read` | `Boolean` | 是否已读 |
| `createdAt` | `created_at` | `String?` | 时间 |
| `postNumber` | `post_number` | `Int?` | 关联楼层 |
| `topicId` | `topic_id` | `Long?` | 关联话题 |
| `slug` | `slug` | `String?` | 话题 slug |
| `data` | `data` | `NotificationData` | 类型相关数据 |
| `fancyTitle` | `fancy_title` | `String?` | 含 HTML 标题 |

派生属性 `title`：
`(fancyTitle ?: data.topicTitle ?: data.displayUsername ?: "")` 去标签。

### 6.3 `NotificationData`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `topicTitle` | `topic_title` | `String?` |
| `displayUsername` | `display_username` | `String?` |
| `originalUsername` | `original_username` | `String?` |
| `username` | `username` | `String?` |
| `username2` | `username2` | `String?` |
| `badgeName` | `badge_name` | `String?` |
| `count` | `count` | `Int?` |
| `groupName` | `group_name` | `String?` |
| `invitedBy` | `invited_by_username` | `String?` |
| `channelTitle` | `chat_channel_title` | `String?` |
| `chatMessageId` | `chat_message_id` | `Long?` |

---

## 7. 搜索

### 7.1 `SearchResponse`

来源：`GET /search.json`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `posts` | `posts` | `List<SearchPost>` | 命中楼层 |
| `topics` | `topics` | `List<Topic>` | 命中话题 |
| `users` | `users` | `List<User>` | 命中用户 |
| `categories` | `categories` | `List<Category>` | 命中分类 |
| `tags` | `tags` | `List<Tag>` | 命中标签 |
| `groups` | `groups` | `List<Group>` | 命中群组 |
| `grouped` | `grouped_search_result` | `GroupedSearch?` | 搜索元信息 |

### 7.2 `SearchPost`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `id` | `id` | `Long` | 楼层 id |
| `name` | `name` | `String?` | 作者名 |
| `username` | `username` | `String` | 用户名 |
| `avatarTemplate` | `avatar_template` | `String?` | 头像模板 |
| `createdAt` | `created_at` | `String?` | 时间 |
| `likeCount` | `like_count` | `Int` | 点赞数 |
| `blurb` | `blurb` | `String` | 命中片段，含 `search-highlight` span |
| `postNumber` | `post_number` | `Int` | 楼层号 |
| `topicId` | `topic_id` | `Long` | 话题 id |

### 7.3 `GroupedSearch`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `term` | `term` | `String` | 搜索词 |
| `searchLogId` | `search_log_id` | `Long` | 搜索日志 id |
| `moreResults` | `more_full_page_results` | `Boolean` | 是否有更多结果 |
| `canCreateTopic` | `can_create_topic` | `Boolean` | 能否直接发帖 |
| `error` | `error` | `String?` | 搜索被拒时的错误（如关键词太短） |

---

## 8. 登录结果

### 8.1 `LoginResponse`

来源：`POST /session`

| 字段 | JSON 名 | 类型 | 说明 |
|---|---|---|---|
| `error` | `error` | `String?` | 失败原因 |
| `reason` | `reason` | `String?` | 另一种失败原因 |
| `waitSeconds` | `wait_seconds` | `Int?` | 限流等待秒数 |

派生属性：`success = (error == null)`。

### 8.2 `SimpleResult`

| 字段 | JSON 名 | 类型 |
|---|---|---|
| `success` | `success` | `String?` |
| `errors` | `errors` | `List<String>?` |
| `error` | `error` | `String?` |

派生属性：`ok = (success != null)`；`message = error ?: errors?.firstOrNull()`。
**当前无调用方**。

---

## 9. `TagOrString`

文件：[TagOrString.kt](../../app/src/main/java/org/linuxdo/android/data/model/TagOrString.kt)

Discourse 的 `tags` 在不同端点有三种形态：

```json
["ai-plugin"]                                        // 话题详情
[{"id":543,"name":"ai-plugin","slug":"ai-plugin"}]    // 列表 / 搜索
[]                                                    // 无标签
```

模型：

```kotlin
@Serializable(with = TagOrStringSerializer::class)
data class TagOrString(val name: String, val id: Long? = null)
```

自定义 `KSerializer` 行为：

| 输入 | 输出 |
|---|---|
| `JsonPrimitive` | `TagOrString(element.content, null)` |
| `JsonObject` | `name` 取 `name` → `slug` → `""`；`id` 取 `id` 并转 `Long?` |
| 其他 | `TagOrString("")` |
| 非 JSON 解码器 | `TagOrString(decoder.decodeString())` |
| 序列化（JSON） | `{"name": "...", "id": 可选}` |
| 序列化（非 JSON） | 只写 `name` 字符串 |

因此 `Topic.tags` 与 `TopicDetail.tags` 可以直接 `map { it.name }` 使用，无需关心服务端形态。

---

## 10. 枚举值与常量

代码中没有为这些值定义 enum，而是硬编码，改动时注意同步：

| 值 | 含义 | 使用位置 |
|---|---|---|
| `post_action_type_id = 2` | 点赞 | `UserRepository.toggleLike()`、`Post.likeCount/liked` |
| `post_action_type_id = 4` | 收藏 | 仅文档说明 |
| `postType == 3` | 小动作楼层 | `Post.isSmallAction` |
| `notification_type` | 见 08 §6 映射表 | `NotificationsScreen.typeLabel()` |
| `period` | `daily/weekly/monthly/quarterly/yearly/all` | `UserRepository.Period` |
| `archetype == "private_message"` | 私信 | 未实现 UI |
