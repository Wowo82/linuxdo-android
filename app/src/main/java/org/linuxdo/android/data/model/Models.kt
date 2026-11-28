package org.linuxdo.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================
// 会话 / 站点
// ============================================================

@Serializable
data class SessionResponse(
    @SerialName("current_user") val currentUser: User? = null,
    @SerialName("all_unread_notifications_count") val allUnreadNotificationsCount: Int = 0,
    @SerialName("unread_high_priority_notifications") val unreadHighPriority: Int = 0,
    @SerialName("unseen_reviewable_count") val unseenReviewable: Int = 0,
    @SerialName("grouped_unread_notifications") val grouped: Map<String, Int> = emptyMap(),
)

@Serializable
data class SiteBasicInfo(
    @SerialName("title") val title: String = "Discourse",
    @SerialName("description") val description: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("logo_small_url") val logoSmallUrl: String? = null,
    @SerialName("mobile_logo_url") val mobileLogoUrl: String? = null,
    @SerialName("favicon_url") val faviconUrl: String? = null,
    @SerialName("apple_touch_icon_url") val appleTouchIconUrl: String? = null,
    @SerialName("login_required") val loginRequired: Boolean = false,
    @SerialName("locale") val locale: String = "en",
)

// ============================================================
// 用户
// ============================================================

@Serializable
data class User(
    val id: Long = 0,
    val username: String = "",
    val name: String? = null,
    @SerialName("avatar_template") val avatarTemplate: String? = null,
    @SerialName("trust_level") val trustLevel: Int = 0,
    val admin: Boolean = false,
    val moderator: Boolean = false,
    @SerialName("title") val title: String? = null,
    @SerialName("primary_group_name") val primaryGroupName: String? = null,
    @SerialName("flair_name") val flairName: String? = null,
    @SerialName("flair_url") val flairUrl: String? = null,
    @SerialName("flair_bg_color") val flairBgColor: String? = null,
    @SerialName("flair_color") val flairColor: String? = null,
    @SerialName("flair_group_id") val flairGroupId: Long? = null,
    /** 仅在 /u/{name}.json 里出现 */
    @SerialName("bio_excerpt") val bioExcerpt: String? = null,
    @SerialName("bio_cooked") val bioCooked: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_posted_at") val lastPostedAt: String? = null,
    @SerialName("time_read") val timeRead: Int? = null,
    @SerialName("badge_count") val badgeCount: Int? = null,
    @SerialName("profile_view_count") val profileViewCount: Int? = null,
    @SerialName("can_send_private_message_to_user") val canSendPm: Boolean = false,
    @SerialName("can_send_private_messages") val canSendPmToUser: Boolean = false,
    val groups: List<Group> = emptyList(),
    @SerialName("user_fields") val userFields: Map<String, String?> = emptyMap(),
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username
    val mainFlair: String? get() = flairName ?: primaryGroupName
}

@Serializable
data class Group(
    val id: Long = 0,
    val name: String = "",
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("flair_url") val flairUrl: String? = null,
    @SerialName("flair_bg_color") val flairBgColor: String? = null,
    @SerialName("flair_color") val flairColor: String? = null,
    @SerialName("user_count") val userCount: Int? = null,
    @SerialName("automatic") val automatic: Boolean = false,
)

@Serializable
data class UserDetailResponse(
    val user: User = User(),
    @SerialName("user_badges") val badges: List<UserBadge> = emptyList(),
    @SerialName("badges") val badgeInfo: List<Badge> = emptyList(),
)

@Serializable
data class UserBadge(
    val id: Long = 0,
    @SerialName("badge_id") val badgeId: Long = 0,
    @SerialName("granted_at") val grantedAt: String? = null,
    val count: Int = 1,
)

@Serializable
data class Badge(
    val id: Long = 0,
    val name: String = "",
    val description: String? = null,
    val icon: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("grant_count") val grantCount: Int = 0,
    @SerialName("badge_type_id") val badgeTypeId: Int = 1,
)

// ============================================================
// 话题列表
// ============================================================

@Serializable
data class TopicListResponse(
    @SerialName("users") val users: List<User> = emptyList(),
    @SerialName("primary_groups") val primaryGroups: List<Group> = emptyList(),
    @SerialName("flair_groups") val flairGroups: List<Group> = emptyList(),
    @SerialName("topic_list") val topicList: TopicList = TopicList(),
)

@Serializable
data class TopicList(
    @SerialName("can_create_topic") val canCreateTopic: Boolean = false,
    @SerialName("per_page") val perPage: Int = 30,
    @SerialName("more_topics_url") val moreTopicsUrl: String? = null,
    @SerialName("top_tags") val topTags: List<Tag> = emptyList(),
    val topics: List<Topic> = emptyList(),
    /** categories 路由会带 */
    @SerialName("category_list") val categoryList: CategoryList? = null,
)

@Serializable
data class Tag(
    val id: Long = 0,
    val name: String = "",
    val slug: String = "",
)

@Serializable
data class Topic(
    val id: Long = 0,
    val title: String = "",
    @SerialName("fancy_title") val fancyTitle: String? = null,
    val slug: String = "",
    @SerialName("posts_count") val postsCount: Int = 0,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("highest_post_number") val highestPostNumber: Int = 0,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_posted_at") val lastPostedAt: String? = null,
    @SerialName("bumped_at") val bumpedAt: String? = null,
    val pinned: Boolean = false,
    @SerialName("pinned_globally") val pinnedGlobally: Boolean = false,
    val closed: Boolean = false,
    val archived: Boolean = false,
    val visible: Boolean = true,
    val unseen: Boolean = false,
    val liked: Boolean? = null,
    val bookmarked: Boolean? = null,
    val excerpt: String? = null,
    val thumbnails: List<Thumbnail>? = null,
    val tags: List<TagOrString> = emptyList(),
    val views: Int = 0,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("has_accepted_answer") val hasAcceptedAnswer: Boolean = false,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("can_vote") val canVote: Boolean = false,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("last_poster_username") val lastPosterUsername: String? = null,
    val posters: List<Poster> = emptyList(),
    @SerialName("archetype") val archetype: String = "regular",
    @SerialName("has_summary") val hasSummary: Boolean = false,
    val unread: Int = 0,
    @SerialName("new_posts") val newPosts: Int = 0,
    @SerialName("last_read_post_number") val lastReadPostNumber: Int? = null,
    @SerialName("notification_level") val notificationLevel: Int? = null,
    @SerialName("unread_posts") val unreadPosts: Int = 0,
) {
    val isUnread: Boolean get() = unread > 0 || newPosts > 0
    val plainTitle: String get() = (fancyTitle ?: title).replace(Regex("<[^>]+>"), "")
    val bestThumb: String? get() =
        thumbnails?.firstOrNull()?.url ?: imageUrl
}

@Serializable
data class Thumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("max_width") val maxWidth: Int? = null,
    @SerialName("max_height") val maxHeight: Int? = null,
)

@Serializable
data class Poster(
    @SerialName("user_id") val userId: Long = 0,
    /** "Original Poster" / "Frequent Poster" / "Most Recent Poster" */
    val description: String = "",
    val extras: String? = null,
) {
    val isOp: Boolean get() = description.contains("Original", true)
    val isLatest: Boolean get() = description.contains("Recent", true)
    val isFrequent: Boolean get() = description.contains("Frequent", true)
}

@Serializable
data class CategoryList(
    @SerialName("can_create_topic") val canCreateTopic: Boolean = false,
    @SerialName("can_create_category") val canCreateCategory: Boolean = false,
    val categories: List<Category> = emptyList(),
)

@Serializable
data class Category(
    val id: Long = 0,
    val name: String = "",
    val slug: String = "",
    val color: String = "0088CC",
    @SerialName("text_color") val textColor: String = "FFFFFF",
    val icon: String? = null,
    val emoji: String? = null,
    @SerialName("style_type") val styleType: String? = null,
    @SerialName("topic_count") val topicCount: Int = 0,
    @SerialName("post_count") val postCount: Int = 0,
    val position: Int = 0,
    @SerialName("description_text") val descriptionText: String? = null,
    @SerialName("description_excerpt") val descriptionExcerpt: String? = null,
    @SerialName("parent_category_id") val parentCategoryId: Long? = null,
    @SerialName("read_restricted") val readRestricted: Boolean = false,
    @SerialName("has_children") val hasChildren: Boolean? = null,
    @SerialName("subcategory_ids") val subcategoryIds: List<Long> = emptyList(),
    @SerialName("uploaded_logo") val uploadedLogo: MediaObject? = null,
    val colorScheme: String? = null,
)

@Serializable
data class MediaObject(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

// ============================================================
// 话题详情
// ============================================================

@Serializable
data class TopicDetail(
    val id: Long = 0,
    val title: String = "",
    @SerialName("fancy_title") val fancyTitle: String? = null,
    val slug: String = "",
    @SerialName("posts_count") val postsCount: Int = 0,
    @SerialName("highest_post_number") val highestPostNumber: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_posted_at") val lastPostedAt: String? = null,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("like_count") val likeCount: Int = 0,
    val views: Int = 0,
    @SerialName("word_count") val wordCount: Int = 0,
    @SerialName("participant_count") val participantCount: Int = 0,
    val closed: Boolean = false,
    val archived: Boolean = false,
    val pinned: Boolean = false,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("user_id") val userId: Long = 0,
    val tags: List<TagOrString> = emptyList(),
    val bookmarked: Boolean = false,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("details") val details: TopicDetails = TopicDetails(),
    @SerialName("post_stream") val postStream: PostStream = PostStream(),
    val draft: Draft? = null,
    @SerialName("accepted_answer") val acceptedAnswer: Boolean = false,
    @SerialName("has_accepted_answer") val hasAcceptedAnswer: Boolean = false,
    @SerialName("can_vote") val canVote: Boolean = false,
    @SerialName("vote_count") val voteCount: Int = 0,
    @SerialName("user_voted") val userVoted: Boolean? = null,
    @SerialName("create_as_shared_issue") val createAsSharedIssue: Boolean? = null,
) {
    val plainTitle: String get() = (fancyTitle ?: title).replace(Regex("<[^>]+>"), "")
}

@Serializable
data class TopicDetails(
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("notification_level") val notificationLevel: Int = 1,
    @SerialName("created_by") val createdBy: User? = null,
    val participants: List<Participant> = emptyList(),
    val links: List<OneboxLink> = emptyList(),
)

@Serializable
data class Participant(
    val id: Long = 0,
    val username: String = "",
    val name: String? = null,
    @SerialName("avatar_template") val avatarTemplate: String? = null,
    @SerialName("post_count") val postCount: Int = 0,
    @SerialName("primary_group_name") val primaryGroupName: String? = null,
    @SerialName("flair_name") val flairName: String? = null,
    @SerialName("flair_url") val flairUrl: String? = null,
    @SerialName("flair_color") val flairColor: String? = null,
    @SerialName("flair_bg_color") val flairBgColor: String? = null,
    @SerialName("flair_group_id") val flairGroupId: Long? = null,
    @SerialName("trust_level") val trustLevel: Int = 0,
)

@Serializable
data class OneboxLink(
    val url: String = "",
    val title: String? = null,
    val clicks: Int = 0,
)

@Serializable
data class PostStream(
    val posts: List<Post> = emptyList(),
    val stream: List<Long> = emptyList(),
)

@Serializable
data class Post(
    val id: Long = 0,
    val name: String? = null,
    val username: String = "",
    @SerialName("avatar_template") val avatarTemplate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** 服务端渲染好的 HTML，直接给 WebView / Html 渲染 */
    val cooked: String = "",
    /** 原始 markdown，编辑时才用 */
    val raw: String? = null,
    @SerialName("post_number") val postNumber: Int = 0,
    @SerialName("post_type") val postType: Int = 1,
    @SerialName("reply_to_post_number") val replyToPostNumber: Int? = null,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("quote_count") val quoteCount: Int = 0,
    @SerialName("incoming_link_count") val incomingLinkCount: Int = 0,
    @SerialName("reads") val reads: Int = 0,
    val version: Int = 1,
    @SerialName("can_edit") val canEdit: Boolean = false,
    @SerialName("can_delete") val canDelete: Boolean = false,
    @SerialName("can_see_hidden_post") val canSeeHiddenPost: Boolean = false,
    @SerialName("can_wiki") val canWiki: Boolean = false,
    @SerialName("user_id") val userId: Long = 0,
    val hidden: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("user_deleted") val userDeleted: Boolean = false,
    @SerialName("edit_reason") val editReason: String? = null,
    @SerialName("can_view_edit_history") val canViewEditHistory: Boolean = false,
    val wiki: Boolean = false,
    @SerialName("post_url") val postUrl: String? = null,
    @SerialName("display_username") val displayUsername: String? = null,
    @SerialName("primary_group_name") val primaryGroupName: String? = null,
    @SerialName("flair_name") val flairName: String? = null,
    @SerialName("flair_url") val flairUrl: String? = null,
    @SerialName("flair_bg_color") val flairBgColor: String? = null,
    @SerialName("flair_color") val flairColor: String? = null,
    @SerialName("flair_group_id") val flairGroupId: Long? = null,
    @SerialName("trust_level") val trustLevel: Int = 0,
    @SerialName("user_title") val userTitle: String? = null,
    @SerialName("title_is_group") val titleIsGroup: Boolean = false,
    @SerialName("actions_summary") val actionsSummary: List<ActionSummary> = emptyList(),
    val moderator: Boolean = false,
    val admin: Boolean = false,
    val staff: Boolean = false,
    @SerialName("accepted_answer") val acceptedAnswer: Boolean = false,
    @SerialName("can_accept_answer") val canAcceptAnswer: Boolean = false,
    @SerialName("can_unaccept_answer") val canUnacceptAnswer: Boolean = false,
    @SerialName("reply_to_user") val replyToUser: ReplyUser? = null,
    @SerialName("link_counts") val linkCounts: List<OneboxLink> = emptyList(),
    @SerialName("yours") val yours: Boolean = false,
    @SerialName("topic_id") val topicId: Long = 0,
    @SerialName("topic_slug") val topicSlug: String? = null,
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: username
    val likeCount: Int
        get() = actionsSummary.firstOrNull { it.id == 2 }?.count ?: 0
    val liked: Boolean
        get() = actionsSummary.firstOrNull { it.id == 2 }?.acted ?: false
    val isOp: Boolean get() = postNumber == 1
    val isSmallAction: Boolean get() = postType == 3
}

@Serializable
data class ReplyUser(
    val id: Long = 0,
    val username: String = "",
    val name: String? = null,
    @SerialName("avatar_template") val avatarTemplate: String? = null,
)

@Serializable
data class ActionSummary(
    val id: Int = 0,
    val count: Int = 0,
    @SerialName("hidden") val hidden: Boolean? = null,
    @SerialName("can_act") val canAct: Boolean? = null,
    @SerialName("acted") val acted: Boolean? = null,
)

@Serializable
data class Draft(
    val sequence: Long = 0,
    @SerialName("reply") val reply: String? = null,
    @SerialName("draft_sequence") val draftSequence: Long? = null,
)

// ============================================================
// 通知
// ============================================================

@Serializable
data class NotificationsResponse(
    val notifications: List<Notification> = emptyList(),
    @SerialName("total_rows_notifications") val totalRows: Int = 0,
    @SerialName("seen_notification_id") val seenNotificationId: Long = 0,
    @SerialName("load_more_notifications") val loadMore: String? = null,
)

@Serializable
data class Notification(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("notification_type") val notificationType: Int = 0,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("post_number") val postNumber: Int? = null,
    @SerialName("topic_id") val topicId: Long? = null,
    @SerialName("slug") val slug: String? = null,
    @SerialName("data") val data: NotificationData = NotificationData(),
    @SerialName("fancy_title") val fancyTitle: String? = null,
) {
    val title: String get() = (fancyTitle ?: data.topicTitle ?: data.displayUsername ?: "").replace(Regex("<[^>]+>"), "")
}

@Serializable
data class NotificationData(
    @SerialName("topic_title") val topicTitle: String? = null,
    @SerialName("display_username") val displayUsername: String? = null,
    @SerialName("original_username") val originalUsername: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("username2") val username2: String? = null,
    @SerialName("badge_name") val badgeName: String? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("invited_by_username") val invitedBy: String? = null,
    @SerialName("chat_channel_title") val channelTitle: String? = null,
    @SerialName("chat_message_id") val chatMessageId: Long? = null,
)

// ============================================================
// 搜索
// ============================================================

@Serializable
data class SearchResponse(
    val posts: List<SearchPost> = emptyList(),
    val topics: List<Topic> = emptyList(),
    val users: List<User> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val groups: List<Group> = emptyList(),
    @SerialName("grouped_search_result") val grouped: GroupedSearch? = null,
)

@Serializable
data class SearchPost(
    val id: Long = 0,
    val name: String? = null,
    val username: String = "",
    @SerialName("avatar_template") val avatarTemplate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("like_count") val likeCount: Int = 0,
    /** 命中片段，含 <span class="search-highlight"> */
    val blurb: String = "",
    @SerialName("post_number") val postNumber: Int = 0,
    @SerialName("topic_id") val topicId: Long = 0,
)

@Serializable
data class GroupedSearch(
    @SerialName("term") val term: String = "",
    @SerialName("search_log_id") val searchLogId: Long = 0,
    @SerialName("more_full_page_results") val moreResults: Boolean = false,
    @SerialName("can_create_topic") val canCreateTopic: Boolean = false,
    val error: String? = null,
)

// ============================================================
// 登录
// ============================================================

@Serializable
data class LoginResponse(
    val error: String? = null,
    val reason: String? = null,
    @SerialName("wait_seconds") val waitSeconds: Int? = null,
    /** 2FA 账号在密码正确但还没提交验证码时会带这些标记 */
    @SerialName("totp_enabled") val totpEnabled: Boolean = false,
    @SerialName("backup_enabled") val backupEnabled: Boolean = false,
    @SerialName("security_key_enabled") val securityKeyEnabled: Boolean = false,
) {
    val success: Boolean get() = error == null

    val secondFactorRequired: Boolean
        get() = totpEnabled || backupEnabled || securityKeyEnabled ||
            reason == "invalid_second_factor" || reason == "invalid_second_factor_method"
}

@Serializable
data class SimpleResult(
    val success: String? = null,
    val errors: List<String>? = null,
    val error: String? = null,
) {
    val ok: Boolean get() = success != null
    val message: String? get() = error ?: errors?.firstOrNull()
}

/** tags 有时是对象数组、有时是字符串数组 */
@Serializable(with = TagOrStringSerializer::class)
data class TagOrString(val name: String, val id: Long? = null)
