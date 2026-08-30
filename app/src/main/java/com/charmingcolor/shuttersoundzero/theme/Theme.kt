package com.charmingcolor.shuttersoundzero.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ShutterZeroLightColorScheme = lightColorScheme(
    primary = BrandBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EDFF),
    onPrimaryContainer = Color(0xFF003D82),
    secondary = Color(0xFF0056B3),
    onSecondary = Color.White,
    background = ScreenBgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceVariantLight,
    surfaceContainerLowest = ScreenBgLight
)

@Composable
fun ShutterSoundZeroTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = ShutterZeroLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
