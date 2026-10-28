package com.ye.webview

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1A56DB), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF545F71), secondaryContainer = Color(0xFFD9E3F8), onSecondaryContainer = Color(0xFF111C2B),
    background = Color(0xFFF7F9FC), surface = Color(0xFFF7F9FC), onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF44474E), outline = Color(0xFF74777F), outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFECEEF3), surfaceContainerHigh = Color(0xFFE6E8EE), surfaceContainerHighest = Color(0xFFE0E2E8),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB2C5FF), onPrimary = Color(0xFF002C6E),
    primaryContainer = Color(0xFF00419B), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFBDC7DC), secondaryContainer = Color(0xFF3D4758), onSecondaryContainer = Color(0xFFD9E3F8),
    background = Color(0xFF111318), surface = Color(0xFF111318), onSurface = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC4C6D0), outline = Color(0xFF8E9099), outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024), surfaceContainerHigh = Color(0xFF282A2F), surfaceContainerHighest = Color(0xFF33353A),
)

@Composable
fun YeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
