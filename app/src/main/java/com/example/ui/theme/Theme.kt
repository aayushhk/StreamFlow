package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = StreamFlowPrimary,
    onPrimary = StreamFlowOnPrimary,
    background = StreamFlowBackground,
    onBackground = StreamFlowOnBackground,
    surface = StreamFlowSurface,
    onSurface = StreamFlowOnBackground,
    surfaceVariant = StreamFlowSurface,
    onSurfaceVariant = StreamFlowOnSurfaceVariant,
    outline = StreamFlowOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
