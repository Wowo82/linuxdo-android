package org.linuxdo.android.ui.common

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

/**
 * Discourse 的 cooked 是服务端渲染好的 HTML，包含 <p> <strong> <a> <img> <pre><code>。
 * 用 TextView + HtmlCompat 渲染是性价比最高的做法：
 *  - 支持基本标签与链接
 *  - 保留代码块等宽
 *  - 不需要引入 WebView（省内存、无 JS 风险）
 *
 * 不支持的是表格 / 内嵌 onebox / 视频，这些留「在浏览器中打开」兜底。
 */
@Composable
fun HtmlContent(
    html: String?,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
    maxLines: Int = Int.MAX_VALUE,
) {
    if (html.isNullOrBlank()) return
    val context = LocalContext.current
    val body = remember(html) {
        // <img> 在 TextView 里显示不了，先剥掉，避免留下一堆空白
        val stripped = html
            .replace(Regex("(?is)<img[^>]*>"), "")
            .replace(Regex("(?is)<video[^>]*>.*?</video>"), "[视频]")
            .replace(Regex("(?is)<iframe[^>]*>.*?</iframe>"), "[嵌入内容]")
            .replace(Regex("(?is)<pre[^>]*>"), "<pre>")
        HtmlCompat.fromHtml(unescapeAll(stripped), HtmlCompat.FROM_HTML_MODE_COMPACT)
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val codeBg = MaterialTheme.colorScheme.surfaceVariant.toArgb()

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                autoLinkMask = Linkify.WEB_URLS
                linksClickable = true
                setLineSpacing(0f, 1.25f)
            }
        },
        update = { tv ->
            tv.textSize = fontSizeSp
            tv.maxLines = maxLines
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            runCatching {
                tv.text = body
            }
        },
    )
}

/**
 * 折叠长帖：超过 N 行先截断，下面给「展开」按钮。
 */
@Composable
fun CollapsibleHtml(
    html: String?,
    collapsedLines: Int = 8,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    HtmlContent(
        html = html,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
    )
    if (!expanded) {
        androidx.compose.material3.TextButton(
            onClick = onToggle,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            androidx.compose.material3.Text("展开全文", fontSize = 12.sp)
        }
    }
}

private fun unescapeAll(html: String): String =
    html.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
