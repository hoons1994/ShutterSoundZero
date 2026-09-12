package com.charmingcolor.shuttersoundzero.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Brand Identity Colors
val BrandBlueLight = Color(0xFF0072DE)
val BrandBlueDark = Color(0xFF529CFF)

val ScreenBgLight = Color(0xFFF2F4F8)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE8ECF2)
val TextPrimaryLight = Color(0xFF111827)
val TextSecondaryLight = Color(0xFF6B7280)

val ScreenBgDark = Color(0xFF080A0E)
val SurfaceDark = Color(0xFF161A22)
val SurfaceVariantDark = Color(0xFF212734)
val TextPrimaryDark = Color(0xFFF3F4F6)
val TextSecondaryDark = Color(0xFF9CA3AF)

// Status Accents
// Foreground status colors are theme-aware so small status text keeps at least
// WCAG AA 4.5:1 contrast against the app's primary light/dark surfaces.
internal val StatusGreenLight = Color(0xFF007A33)
internal val StatusGreenDark = Color(0xFF00A044)
val StatusGreenBgLight = Color(0xFFE8F5E9)
val StatusGreenBgDark = Color(0xFF112918)

internal val StatusAmberLight = Color(0xFF8A5A00)
internal val StatusAmberDark = Color(0xFFF59E0B)
val StatusAmberBgLight = Color(0xFFFEF3C7)
val StatusAmberBgDark = Color(0xFF2D230E)

val StatusRed = Color(0xFFEF4444)
val StatusRedBgLight = Color(0xFFFEE2E2)
val StatusRedBgDark = Color(0xFF2C1515)

val StatusGreen: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.surface == SurfaceDark) {
        StatusGreenDark
    } else {
        StatusGreenLight
    }

val StatusAmber: Color
    @Composable
    @ReadOnlyComposable
    get() = if (MaterialTheme.colorScheme.surface == SurfaceDark) {
        StatusAmberDark
    } else {
        StatusAmberLight
    }
