package com.charmingcolor.shuttersoundzero.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
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

private val ShutterZeroDarkColorScheme = darkColorScheme(
    primary = BrandBlueDark,
    onPrimary = Color(0xFF001B3D),
    primaryContainer = Color(0xFF173A63),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF9CCAFF),
    onSecondary = Color(0xFF002F5C),
    background = ScreenBgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerLowest = ScreenBgDark
)

@Composable
fun ShutterSoundZeroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        ShutterZeroDarkColorScheme
    } else {
        ShutterZeroLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
