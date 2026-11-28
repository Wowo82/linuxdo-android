package org.linuxdo.android.ui.browse

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.model.Category
import org.linuxdo.android.data.model.Topic
import org.linuxdo.android.ui.simpleFactory
import org.linuxdo.android.ui.theme.parseHexColor
import org.linuxdo.android.util.shortCount

/**
 * 版块页：顶级分类 + 其子版块。
 * 点任意分类进入该分类下的话题列表（复用 FeedScreen 的筛选与无限滚动）。
 */
@Composable
fun CategoriesScreen(
    container: AppContainer,
    onOpenCategory: (Category) -> Unit,
) {
    // 用 ViewModel 而不是 remember：旋转屏幕 / 重进 tab 不该重拉整棵分类树，
    // 而且项目里其它页面都是 ViewModel + StateFlow，保持一致。
    val vm: CategoriesViewModel = viewModel(
        key = "categories",
        factory = simpleFactory { CategoriesViewModel(container.repository) },
    )
    val state by vm.state.collectState()
    val categories = state.categories

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    when {
        state.loading && categories.isEmpty() -> CenterLoading()
        state.error != null && categories.isEmpty() ->
            ErrorBox(state.error.orEmpty()) { vm.reload() }
        categories.isEmpty() -> EmptyBox("没有可见的版块")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            categories.forEach { parent ->
                item { CategoryRow(parent) { onOpenCategory(parent) } }
                parent.subcategoryList?.forEach { child ->
                    item { CategoryRow(child, indented = true) { onOpenCategory(child) } }
                }
                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    indented: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = if (indented) 30.dp else 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (indented) 8.dp else 11.dp)
                .clip(CircleShape)
                .background(parseHexColor(category.color)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                category.name,
                fontSize = if (indented) 14.sp else 15.5.sp,
                fontWeight = if (indented) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            category.descriptionText?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            category.topicCount.shortCount(),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
    }
}

/** 单个版块的话题列表：顶部返回 + 分类名，内容直接复用 FeedScreen。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTopicsScreen(
    category: Category,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
) {
    val vm: FeedViewModel = viewModel(
        key = "feed_category_${category.id}",
        factory = simpleFactory {
            FeedViewModel(
                repo = container.repository,
                initialCategoryId = category.id,
                categorySlug = category.slug,
            )
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        category.name,
                        fontSize = 16.sp,
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
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            FeedScreen(vm = vm, onOpenTopic = onOpenTopic)
        }
    }
}
