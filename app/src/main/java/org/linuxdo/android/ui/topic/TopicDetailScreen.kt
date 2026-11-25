package org.linuxdo.android.ui.topic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collect
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.model.Post
import org.linuxdo.android.ui.browse.CenterLoading
import org.linuxdo.android.ui.browse.EmptyBox
import org.linuxdo.android.ui.browse.ErrorBox
import org.linuxdo.android.ui.browse.collectState
import org.linuxdo.android.ui.browse.snapshotOf
import org.linuxdo.android.ui.common.HtmlContent
import org.linuxdo.android.ui.simpleFactory
import org.linuxdo.android.ui.theme.AvatarFallback
import org.linuxdo.android.ui.theme.CategoryChip
import org.linuxdo.android.ui.theme.defaultCategoryColor
import org.linuxdo.android.util.TimeFmt
import org.linuxdo.android.util.absolutize
import org.linuxdo.android.util.avatarUrl
import org.linuxdo.android.util.shortCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    topicId: Long,
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: TopicDetailViewModel = viewModel(
        key = "topic_$topicId",
        factory = simpleFactory { TopicDetailViewModel(container.repository, topicId) },
    )
    val state by vm.state.collectState()
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var showComposer by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.posts.size - 2) vm.loadMore()
            }
    }

    val detail = state.detail

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        detail?.plainTitle ?: "话题",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.toggleBookmark() },
                        enabled = detail != null && !state.bookmarking,
                    ) {
                        if (detail?.bookmarked == true) {
                            Icon(
                                Icons.Filled.Bookmark,
                                "取消收藏",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Outlined.BookmarkBorder, "收藏")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            if (showComposer) {
                ReplyComposer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    replyTo = state.replyTo,
                    sending = state.sending,
                    error = state.sendError,
                    onCancel = {
                        vm.setReplyTo(null)
                        showComposer = false
                    },
                    onSend = {
                        vm.sendReply(draft) { ok ->
                            if (ok) {
                                draft = ""
                                showComposer = false
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.posts.isEmpty() -> CenterLoading()
                state.error != null && state.posts.isEmpty() ->
                    ErrorBox(state.error.orEmpty(), onRetry = { vm.refresh() })
                state.posts.isEmpty() -> EmptyBox("这个帖子里还没有内容")
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item(key = "header") {
                        TopicHeader(
                            title = detail?.plainTitle ?: "",
                            tags = detail?.tags?.map { it.name } ?: emptyList(),
                            views = detail?.views ?: 0,
                            replies = detail?.replyCount ?: 0,
                            likes = detail?.likeCount ?: 0,
                        )
                    }
                    items(state.posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            liked = state.likedOf(post),
                            likeCount = state.likeCountOf(post),
                            onLike = { vm.toggleLike(post) },
                            onReply = {
                                vm.setReplyTo(post)
                                showComposer = true
                            },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                            thickness = 0.5.dp,
                        )
                    }
                    item(key = "footer") {
                        Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                            Text(
                                if (state.posts.size >= (detail?.postsCount ?: 0)) "已经到底啦"
                                else "加载中…",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }

            if (!showComposer && state.posts.isNotEmpty()) {
                Surface(
                    onClick = { showComposer = true },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "回复",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicHeader(
    title: String,
    tags: List<String>,
    views: Int,
    replies: Int,
    likes: Int,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            title,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.take(4).forEach { t ->
                    CategoryChip(name = t, color = defaultCategoryColor())
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                "$replies 回复 · $views 浏览 · $likes 赞",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}

@Composable
private fun PostCard(
    post: Post,
    liked: Boolean,
    likeCount: Int,
    onLike: () -> Unit,
    onReply: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.displayName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    post.userTitle?.takeIf { it.isNotBlank() }?.let { t ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            t,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    TimeFmt.relative(post.createdAt) +
                        if (post.version > 1) " · 已编辑" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
            Text(
                "#${post.postNumber}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }

        Spacer(Modifier.height(8.dp))

        post.replyToUser?.let { ru ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    null,
                    Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "回复 ${ru.name?.takeIf { it.isNotBlank() } ?: ru.username}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        HtmlContent(html = post.cooked, fontSizeSp = 15f)

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onLike() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    "点赞",
                    Modifier.size(14.dp),
                    tint = if (liked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
                if (likeCount > 0) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        likeCount.shortCount(),
                        fontSize = 11.5.sp,
                        color = if (liked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onReply() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Reply,
                    "回复",
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun Avatar(post: Post) {
    val url = avatarUrl(post.avatarTemplate, 96)
    Box(Modifier.size(34.dp)) {
        AvatarFallback(name = post.displayName, size = 34)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

@Composable
private fun ReplyComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    replyTo: Post?,
    sending: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.imePadding(),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            if (replyTo != null) {
                Text(
                    "回复 #${replyTo.postNumber} ${replyTo.displayName}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
            }
            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth().height(120.dp),
                placeholder = { Text("说点什么…（支持 Markdown）", fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(10.dp),
            )
            error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = onCancel,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        "取消",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onSend,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    enabled = !sending && draft.isNotBlank(),
                ) {
                    Box(
                        Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                "发送",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
