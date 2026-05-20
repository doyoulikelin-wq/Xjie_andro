package com.xjie.app.feature.elderly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

/**
 * 首页"关怀复查"卡片。由父视图根据 elderlyMode 决定是否渲染；卡片自身负责加载今日状态。
 */
@Composable
fun ElderlyCareCard(
    onOpenHistory: () -> Unit,
    vm: ElderlyViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.loadStatus(autoPrompt = true) }

    val status = state.status
    val quickReviews = listOf(
        QuickReview("💊", "用药签到", "已按时服药"),
        QuickReview("😴", "睡眠复查", "昨夜睡眠"),
        QuickReview("💧", "饮水复查", "饮水充足"),
        QuickReview("🚶", "活动复查", "今日散步"),
    )

    Column(Modifier.cardStyle()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Favorite, null, tint = XjiePalette.Accent)
            Spacer(Modifier.width(6.dp))
            Text(
                "关怀复查",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            if (status != null) {
                Text(
                    "今日已签到 ${status.today_count} 次",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val tipText = when {
            status == null -> "正在加载今日关怀状态…"
            status.should_prompt && status.last_checkin_at != null ->
                "距离上次签到已超过 ${status.interval_min / 60} 小时，过来打个招呼吧～"
            status.should_prompt -> "该和您聊一聊啦 ❤️ 点击下方任一选项快速复查。"
            status.last_checkin_at != null -> "感谢分享！稍后我们再来问候您。"
            else -> "随时记录身体和心情变化。"
        }
        Text(tipText, fontSize = 17.sp)

        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickReviews.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { q ->
                        OutlinedButton(
                            onClick = { vm.openSheet("manual_${q.label}", presetActivity = q.activity) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("${q.emoji} ${q.label}", fontSize = 15.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.openSheet("manual") },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("综合签到", fontSize = 17.sp)
            }
            OutlinedButton(
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.History, null)
                Spacer(Modifier.width(4.dp))
                Text("查看历史", fontSize = 17.sp)
            }
        }
    }

    if (state.showSheet) {
        ElderlyCheckinDialog(
            vm = vm,
            source = state.sheetSource,
            initialActivity = state.sheetPresetActivity,
            onDismiss = { vm.closeSheet() },
        )
    }
}

private data class QuickReview(val emoji: String, val label: String, val activity: String)
