package org.linuxdo.android.ui.topic

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.model.Category

/**
 * 发新帖。分类必选（Discourse 不允许无分类发帖，除非站点改过设置）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ComposerScreen(
    container: AppContainer,
    onDismiss: () -> Unit,
    onPosted: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<Category?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { container.repository.categories() }.onSuccess {
            categories = it.categories.filter { c -> c.readRestricted.not() || true }
            selected = categories.firstOrNull { c -> c.parentCategoryId == null }
        }
    }

    val canPost = title.trim().length >= 4 && body.trim().length >= 10 &&
        selected != null && !sending

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发新帖", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "关闭")
                    }
                },
                actions = {
                    Text(
                        "发布",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canPost) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .then(
                                if (canPost) Modifier.clickableNoRipple {
                                    sending = true
                                    error = null
                                    scope.launch {
                                        container.repository.createTopic(
                                            title = title.trim(),
                                            raw = body.trim(),
                                            categoryId = selected!!.id,
                                        ).onSuccess {
                                            sending = false
                                            onPosted()
                                        }.onFailure {
                                            sending = false
                                            error = it.message ?: "发布失败"
                                        }
                                    }
                                } else Modifier
                            ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            TextField(
                value = title,
                onValueChange = { if (it.length <= 120) title = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("标题（4-120 字）", fontSize = 14.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(10.dp),
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "选择分类",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.filter { it.parentCategoryId == null }.forEach { c ->
                    FilterChip(
                        selected = selected?.id == c.id,
                        onClick = { selected = c },
                        label = { Text(c.name, fontSize = 12.sp) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().height(280.dp),
                placeholder = { Text("正文（支持 Markdown）", fontSize = 14.sp) },
                shape = RoundedCornerShape(10.dp),
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            if (sending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("发布中…", fontSize = 12.5.sp)
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    )
