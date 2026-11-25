package org.linuxdo.android.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.model.Notification
import org.linuxdo.android.util.TimeFmt
import org.linuxdo.android.util.shortCount

/** Discourse 通知类型 id → 中文标签 + 图标 emoji */
private fun typeLabel(type: Int): Pair<String, String> = when (type) {
    1 -> "提到了你" to "@"
    2 -> "回复了你" to "💬"
    3 -> "引用了你" to "❝"
    4 -> "编辑了你的帖子" to "✎"
    5 -> "赞了你" to "♥"
    6, 7 -> "私信" to "✉"
    8 -> "邀请已接受" to "✓"
    9 -> "发帖了" to "＋"
    10 -> "移动了帖子" to "→"
    11 -> "链接到" to "🔗"
    12 -> "获得徽章" to "🏅"
    13 -> "邀请你加入话题" to "✉"
    15 -> "群组提到" to "@"
    16 -> "群组消息" to "👥"
    17 -> "关注的首帖" to "★"
    18 -> "话题提醒" to "⏰"
    19 -> "赞（合并）" to "♥"
    24 -> "书签提醒" to "🔖"
    25 -> "回应" to "😀"
    29, 30, 31, 32, 33, 40 -> "聊天" to "💬"
    800, 801, 802 -> "关注动态" to "👤"
    else -> "通知" to "•"
}

@Composable
fun NotificationsScreen(
    container: AppContainer,
    onOpenTopic: (Long) -> Unit,
    onRead: () -> Unit,
) {
    var items by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true
        error = null
        runCatching { container.repository.notifications() }
            .onSuccess {
                items = it.notifications
                loading = false
                onRead()
            }
            .onFailure {
                error = if (container.sessionStore.isLoggedIn) {
                    it.message ?: "加载失败"
                } else {
                    "登录后查看通知"
                }
                loading = false
            }
    }

    LaunchedEffect(Unit) { load() }

    when {
        loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }

        error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            val msg = error ?: ""
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                Spacer(Modifier.height(10.dp))
                if (container.sessionStore.isLoggedIn) {
                    Text(
                        "重试",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { scope.launch { load() } },
                    )
                }
            }
        }

        items.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "还没有通知",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            )
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { n ->
                NotificationRow(n) {
                    n.topicId?.let(onOpenTopic)
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    thickness = 0.5.dp,
                )
            }
            item {
                Box(Modifier.fillMaxWidth().padding(18.dp), Alignment.Center) {
                    Text(
                        "已读到最新",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, onClick: () -> Unit) {
    val (label, emoji) = typeLabel(n.notificationType)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = n.topicId != null, onClick = onClick)
            .background(
                if (!n.read) MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(emoji, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                )
                n.data.count?.takeIf { it > 1 }?.let {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "×${it.shortCount()}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
            }
            if (n.title.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    n.title,
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            n.data.displayUsername?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    "@$it",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            TimeFmt.relative(n.createdAt),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
