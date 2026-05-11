package com.xjie.app.feature.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.xjie.app.R
import kotlinx.coroutines.delay

/**
 * 启动动画：渐变背景 + Logo 弹入 + 双层光环脉冲 + 文案上滑。
 * 显示约 1500ms 后调用 [onFinished]。
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    var showText by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Logo 弹入
        logoAlpha.animateTo(1f, tween(420, easing = LinearEasing))
        logoScale.animateTo(1f, tween(620, easing = EaseOutBack))
        showText = true
        delay(900)
        onFinished()
    }

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0E7C66),
            Color(0xFF14B8A6),
            Color(0xFF67E8F9),
        ),
        start = Offset(0f, 0f),
        end = Offset.Infinite,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        // 双层脉冲光环
        PulseRings()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value),
            )
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(tween(420)) + slideInVertically(tween(480)) { it / 2 },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "小捷",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "你的智能健康管家",
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PulseRings() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val p1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart,
        ),
        label = "p1",
    )
    val p2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, delayMillis = 600, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart,
        ),
        label = "p2",
    )

    Canvas(modifier = Modifier.size(320.dp)) {
        val maxR = size.minDimension / 2f
        listOf(p1, p2).forEach { p ->
            val radius = 40f + (maxR - 40f) * p
            val alpha = (1f - p).coerceIn(0f, 1f) * 0.55f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                style = Stroke(width = 3f),
            )
        }
    }
}

@Composable
fun SplashOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(360)),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 占位空 — 通过 SplashScreen 提供内容；这里仅做容器以便淡出
        }
    }
}
