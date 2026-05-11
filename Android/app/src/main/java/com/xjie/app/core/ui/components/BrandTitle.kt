package com.xjie.app.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.xjie.app.core.ui.theme.XjiePalette

/**
 * 主功能页顶部统一标题。
 * 保留现有品牌配色，改为更稳的黑体加粗风格。
 */
@Composable
fun BrandTitle(
    text: String,
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        XjiePalette.Accent.copy(alpha = 0.82f),
    ),
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = Brush.linearGradient(colors),
            fontSize = 22.sp,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.2.sp,
            lineHeight = 26.sp,
        ),
    )
}
