package com.systemverktoy.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val SystemverktoyColors = darkColorScheme(
    background = Color(0xFF0F1317),
    surface = Color(0xFF182028),
    surfaceVariant = Color(0xFF212B35),
    onBackground = Color(0xFFEEF1F4),
    onSurface = Color(0xFFEEF1F4),
    onSurfaceVariant = Color(0xFFA3ADB8),
)

@Composable
fun SystemverktoyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SystemverktoyColors, content = content)
}
