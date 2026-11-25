package org.linuxdo.android.ui.browse

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.linuxdo.android.data.UserRepository
import org.linuxdo.android.data.model.Category
import org.linuxdo.android.data.model.Topic
import org.linuxdo.android.ui.theme.AvatarFallback
import org.linuxdo.android.ui.theme.CategoryChip
import org.linuxdo.android.ui.theme.parseHexColor
import org.linuxdo.android.util.Html
import org.linuxdo.android.util.TimeFmt
import org.linuxdo.android.util.absolutize
import org.linuxdo.android.util.shortCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    vm: FeedViewModel,
    onOpenTopic: (Topic) -> Unit,
) {
    val state by vm.state.collectState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    LaunchedEffect(listState) {
        snapshotOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.topics.size - 3) vm.loadMore()
            }
    }

    Column(Modifier.fillMaxSize()) {
        FilterBar(
            state = state,
            onFilter = { vm.setFilter(it) },
            onPeriod = { vm.setPeriod(it) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading && state.topics.isEmpty() -> CenterLoading()
                state.error != null && state.topics.isEmpty() ->
                    ErrorBox(message = state.error.orEmpty(), onRetry = { vm.refresh() })
                state.topics.isEmpty() -> EmptyBox("这里还没有内容")
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.topics, key = { it.id }) { topic ->
                        TopicRow(
                            topic = topic,
                            category = vm.categoryFor(topic),
                            onClick = { onOpenTopic(topic) },
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                        )
                    }
                    if (state.loadingMore) item { CenterLoading(height = 64.dp) }
                    if (state.endReached && state.topics.isNotEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "已经到底啦",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    state: FeedState,
    onFilter: (UserRepository.ListFilter) -> Unit,
    onPeriod: (UserRepository.Period) -> Unit,
) {
    val filters = listOf(
        UserRepository.ListFilter.LATEST to "最新",
        UserRepository.ListFilter.HOT to "热门",
        UserRepository.ListFilter.NEW to "新帖",
        UserRepository.ListFilter.UNREAD to "未读",
        UserRepository.ListFilter.TOP to "精华",
    )
    val selected = filters.indexOfFirst { it.first == state.filter }.coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selected,
        edgePadding = 8.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        divider = {},
    ) {
        filters.forEach { (f, label) ->
            Tab(
                selected = state.filter == f,
                onClick = { onFilter(f) },
                text = {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (state.filter == f) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }

    if (state.filter == UserRepository.ListFilter.TOP) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UserRepository.Period.entries.forEach { p ->
                val name = when (p) {
                    UserRepository.Period.DAILY -> "日"
                    UserRepository.Period.WEEKLY -> "周"
                    UserRepository.Period.MONTHLY -> "月"
                    UserRepository.Period.QUARTERLY -> "季"
                    UserRepository.Period.YEARLY -> "年"
                    UserRepository.Period.ALL -> "全部"
                }
                val on = state.period == p
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent
                        )
                        .clickable { onPeriod(p) }
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(
                        name,
                        fontSize = 12.sp,
                        color = if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicRow(
    topic: Topic,
    category: Category?,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    if (topic.pinned || category != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (topic.pinned) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = "置顶",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            category?.let {
                                CategoryChip(
                                    name = it.name,
                                    color = parseHexColor(it.color),
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = topic.plainTitle,
                        fontSize = 15.5.sp,
                        lineHeight = 21.sp,
                        fontWeight = if (topic.isUnread) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!topic.excerpt.isNullOrBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = Html.toPlain(topic.excerpt),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
                topic.bestThumb?.let { img ->
                    Spacer(Modifier.width(10.dp))
                    AsyncImage(
                        model = absolutize(img),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarFallback(name = topic.lastPosterUsername ?: "?", size = 16)
                Spacer(Modifier.width(5.dp))
                Text(
                    text = topic.lastPosterUsername ?: "",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    null,
                    Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    topic.replyCount.shortCount(),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Outlined.Whatshot,
                    null,
                    Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    topic.views.shortCount(),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    TimeFmt.relative(topic.bumpedAt ?: topic.lastPostedAt ?: topic.createdAt),
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
internal fun CenterLoading(height: Dp = 200.dp) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Text(
                    "重试",
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
        )
    }
}
