package org.linuxdo.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linuxdo.android.AppContainer
import org.linuxdo.android.ui.browse.collectState
import org.linuxdo.android.ui.simpleFactory
import org.linuxdo.android.util.Html
import org.linuxdo.android.util.TimeFmt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    container: AppContainer,
    onOpenTopic: (Long) -> Unit,
) {
    val vm: SearchViewModel = viewModel(
        key = "search",
        factory = simpleFactory { SearchViewModel(container.repository, container.searchHistoryStore) },
    )
    val state by vm.state.collectState()
    var input by remember { mutableStateOf(state.query) }

    Column(Modifier.fillMaxSize()) {
        // 搜索框
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = input,
                    onValueChange = {
                        input = it
                        vm.updateQuery(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("搜索话题、用户…", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            null,
                            Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    },
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = ""; vm.updateQuery("") }) {
                                Icon(Icons.Outlined.Close, "清空", Modifier.size(16.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.submit(input) }),
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            !state.searched -> HistoryList(
                history = state.history,
                onPick = { input = it; vm.submit(it) },
                onClear = { vm.clearHistory() },
            )

            state.results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    state.error ?: "没有找到相关内容",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.results, key = { it.id }) { hit ->
                    val topicTitle = vm.topicTitleFor(hit.topicId)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTopic(hit.topicId) }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        if (topicTitle.isNotBlank()) {
                            Text(
                                topicTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            Html.toPlain(hit.blurb),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${hit.name?.takeIf { it.isNotBlank() } ?: hit.username} · " +
                                "#${hit.postNumber} · ${TimeFmt.relative(hit.createdAt)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryList(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "输入关键词开始搜索\n支持中文、用户 @name、#分类",
                fontSize = 12.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "搜索历史",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "清空",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onClear() },
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { term ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPick(term) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                ) {
                    Text(term, fontSize = 12.sp)
                }
            }
        }
    }
}
