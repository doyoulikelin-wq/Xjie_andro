package com.xjie.app.core.ui.theme

import androidx.compose.ui.graphics.Color

/** 品牌色 — 与 iOS [Theme.swift] 对齐 */
object XjiePalette {
    val Primary = Color(0xFF1456C8)         // 深蓝
    val Accent = Color(0xFF12B6A2)          // 青绿
    val GradientStart = Color(0xFF73D5FF)
    val GradientEnd = Primary
    val Danger = Color(0xFFD84C4C)
    val Success = Color(0xFF16A66A)
    val Warning = Color(0xFFE5A33C)
}

// Light scheme
val LightPrimary = XjiePalette.Primary
val LightSecondary = XjiePalette.Accent
val LightBackground = Color(0xFFF3F6FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF5F8FC)
val LightPrimaryContainer = Color(0xFFDDE9FF)
val LightSecondaryContainer = Color(0xFFD7F4EE)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightOnPrimaryContainer = Color(0xFF0D2D63)
val LightOnSecondaryContainer = Color(0xFF083C36)
val LightOnBackground = Color(0xFF1A1C1E)
val LightOnSurface = Color(0xFF1A1C1E)
val LightOnSurfaceVariant = Color(0xFF5C6878)
val LightOutline = Color(0xFFD3DCE7)
val LightOutlineVariant = Color(0xFFE6EDF5)

// Dark scheme
val DarkPrimary = Color(0xFF82B6FF)
val DarkSecondary = Color(0xFF52DBC8)
val DarkBackground = Color(0xFF0B1220)
val DarkSurface = Color(0xFF101A2B)
val DarkSurfaceVariant = Color(0xFF132035)
val DarkPrimaryContainer = Color(0xFF14356A)
val DarkSecondaryContainer = Color(0xFF0E433D)
val DarkOnPrimary = Color(0xFF002E5F)
val DarkOnPrimaryContainer = Color(0xFFDDE9FF)
val DarkOnSecondaryContainer = Color(0xFFD7F4EE)
val DarkOnBackground = Color(0xFFE3E5E8)
val DarkOnSurface = Color(0xFFE3E5E8)
val DarkOnSurfaceVariant = Color(0xFFA7B3C5)
val DarkOutline = Color(0xFF324155)
val DarkOutlineVariant = Color(0xFF1B2A42)
