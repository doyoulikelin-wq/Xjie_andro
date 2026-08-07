package com.xjie.app.core.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class XjiePaletteParityTest {
    @Test
    fun brandAndLightSurfaceTokensMatchCurrentIosTheme() {
        assertEquals(0xFF1565C0.toInt(), XjiePalette.Primary.toArgb())
        assertEquals(0xFF00C9A7.toInt(), XjiePalette.Accent.toArgb())
        assertEquals(0xFF00C9A7.toInt(), XjiePalette.GradientStart.toArgb())
        assertEquals(0xFF1565C0.toInt(), XjiePalette.GradientEnd.toArgb())
        assertEquals(0xFFEF4444.toInt(), XjiePalette.Danger.toArgb())
        assertEquals(0xFF22C55E.toInt(), XjiePalette.Success.toArgb())
        assertEquals(0xFFF59E0B.toInt(), XjiePalette.Warning.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), LightBackground.toArgb())
        assertEquals(0xFFFFFFFF.toInt(), LightSurface.toArgb())
        assertEquals(0xFFF3F8FC.toInt(), LightSurfaceVariant.toArgb())
    }
}
