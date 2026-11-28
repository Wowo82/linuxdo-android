package org.linuxdo.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linuxdo.android.AppContainer
import org.linuxdo.android.ui.browse.FeedViewModel
import org.linuxdo.android.ui.browse.FeedScreen
import org.linuxdo.android.ui.notifications.NotificationsScreen
import org.linuxdo.android.ui.profile.ProfileScreen
import org.linuxdo.android.ui.search.SearchScreen
import org.linuxdo.android.ui.topic.ComposerScreen
import org.linuxdo.android.ui.topic.TopicDetailScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    TOPICS("话题", Icons.Outlined.Explore),
    SEARCH("搜索", Icons.Outlined.Search),
    NOTIFICATIONS("通知", Icons.Outlined.Notifications),
    ME("我的", Icons.Outlined.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    vm: MainViewModel,
    state: MainState,
    container: AppContainer,
    deepLinkTopicId: Long? = null,
    deepLinkPostNumber: Int? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }
    var openTopicId by remember { mutableStateOf<Long?>(null) }
    var openPostNumber by remember { mutableStateOf<Int?>(null) }
    var composing by remember { mutableStateOf(false) }

    // 外部链接进来：直接打开对应话题，并停在指定楼层
    LaunchedEffect(deepLinkTopicId, deepLinkPostNumber) {
        val id = deepLinkTopicId ?: return@LaunchedEffect
        openTopicId = id
        openPostNumber = deepLinkPostNumber
        onDeepLinkConsumed()
    }

    val feedVm: FeedViewModel = viewModel(
        key = "feed",
        factory = simpleFactory { FeedViewModel(container.repository) },
    )

    val title = when {
        openTopicId != null -> ""
        tab == 0 -> state.siteTitle
        tab == 1 -> "搜索"
        tab == 2 -> "通知"
        else -> "我的"
    }

    if (openTopicId != null) {
        TopicDetailScreen(
            topicId = openTopicId!!,
            container = container,
            initialPostNumber = openPostNumber,
            onBack = {
                openTopicId = null
                openPostNumber = null
            },
        )
        return
    }

    if (composing) {
        ComposerScreen(
            container = container,
            onDismiss = { composing = false },
            onPosted = { composing = false },
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEachIndexed { index, t ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = {
                            tab = index
                            if (index == 0) feedVm.ensureLoaded()
                        },
                        icon = {
                            if (t == Tab.NOTIFICATIONS && state.unreadNotifications > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text(state.unreadNotifications.toString()) }
                                    }
                                ) { Icon(t.icon, t.label) }
                            } else {
                                Icon(t.icon, t.label)
                            }
                        },
                        label = { Text(t.label, fontSize = 11.sp) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { composing = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, "发帖")
                    Text("  发帖", fontSize = 14.sp)
                }
            }
        },
    ) { padding ->
        Surface(
            Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> FeedScreen(vm = feedVm, onOpenTopic = { t -> openTopicId = t.id })
                    1 -> SearchScreen(
                        container = container,
                        onOpenTopic = { id -> openTopicId = id },
                    )
                    2 -> NotificationsScreen(
                        container = container,
                        onOpenTopic = { id -> openTopicId = id },
                        onRead = { vm.refreshUnread() },
                    )
                    else -> ProfileScreen(
                        container = container,
                        user = state.user,
                        siteTitle = state.siteTitle,
                        onLoggedIn = { u -> vm.setUser(u); feedVm.refresh() },
                        onLogout = { vm.logout() },
                    )
                }
            }
        }
    }
}

/** 极简 ViewModel 工厂：只用于无参构造的 VM */
@Suppress("UNCHECKED_CAST")
inline fun <reified T : androidx.lifecycle.ViewModel> simpleFactory(
    crossinline creator: () -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    override fun <VM : androidx.lifecycle.ViewModel> create(modelClass: Class<VM>): VM =
        creator() as VM
}
