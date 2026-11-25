package org.linuxdo.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Accent = Color(0xFF33D17A)
private val AccentDark = Color(0xFF2BB86A)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5E4),
    onPrimaryContainer = Color(0xFF07291A),
    secondary = Color(0xFF4A6572),
    surface = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8F9),
    surfaceVariant = Color(0xFFF0F2F4),
    outline = Color(0xFFD6DADE),
    error = Color(0xFFD9453B),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06231A),
    primaryContainer = Color(0xFF10372A),
    onPrimaryContainer = Color(0xFFC6F5DE),
    secondary = Color(0xFF9DB2BC),
    surface = Color(0xFF16191D),
    background = Color(0xFF0F1115),
    surfaceVariant = Color(0xFF1E2227),
    outline = Color(0xFF2E343A),
    error = Color(0xFFFF6B5E),
)

@Composable
fun LinuxDoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
