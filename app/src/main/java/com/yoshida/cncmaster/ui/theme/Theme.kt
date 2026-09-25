package com.yoshida.cncmaster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CncDarkColors = darkColorScheme(
    primary = Color(0xFFFFB000),
    onPrimary = Color(0xFF17120A),
    secondary = Color(0xFF82D8FF),
    background = Color(0xFF0A0E12),
    onBackground = Color(0xFFE8EEF3),
    surface = Color(0xFF111820),
    onSurface = Color(0xFFE8EEF3),
    surfaceVariant = Color(0xFF18222C),
    outline = Color(0xFF394A58)
)

@Composable
fun CNCMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CncDarkColors,
        content = content
    )
}
