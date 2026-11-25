package org.linuxdo.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 解析 Discourse 的 "RRGGBB" 颜色串 */
fun parseHexColor(hex: String?, fallback: Color = Color(0xFF8699A2)): Color {
    if (hex.isNullOrBlank()) return fallback
    val s = hex.removePrefix("#")
    return runCatching {
        if (s.length == 6) Color(0xFF000000 or s.toLong(16)) else fallback
    }.getOrDefault(fallback)
}

/** 按背景亮度决定前景用黑还是白 */
fun onColorFor(bg: Color): Color {
    val luminance = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
    return if (luminance > 0.6) Color(0xFF111111) else Color.White
}

/**
 * 头像兜底：无网络或图片加载失败时显示首字母 + 由用户名派生的渐变色。
 * 有头像 URL 的地方用 AsyncImage 包它。
 */
@Composable
fun AvatarFallback(name: String, size: Int = 36) {
    val seed = name.hashCode() and 0x7fffffff
    val hue = (seed % 360).toFloat()
    val start = Color.hsl(hue, 0.55f, 0.62f)
    val end = Color.hsl((hue + 40f) % 360f, 0.55f, 0.48f)
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            color = Color.White,
            fontSize = (size * 0.45f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 分类标签 */
@Composable
fun CategoryChip(name: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f)),
    ) {
        Text(
            text = name,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 没有分类信息时用的中性色 */
fun defaultCategoryColor(): Color = Color(0xFF8699A2)
