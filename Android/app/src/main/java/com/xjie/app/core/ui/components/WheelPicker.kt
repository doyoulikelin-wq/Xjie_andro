package com.xjie.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 简易竖向滚轮选择器：固定可见 5 行，居中行高亮，松手自动吸附到最近一项。
 *
 * @param items 可选项展示文本列表（直接展示，调用方负责单位/格式）。
 * @param selectedIndex 当前选中下标。
 * @param onSelected 选中下标回调。
 * @param itemHeight 单项高度。
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleItemCount: Int = 5,
) {
    require(visibleItemCount % 2 == 1) { "visibleItemCount must be odd" }
    val padCount = visibleItemCount / 2
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = state)

    // 当外部 selectedIndex 变化时，平滑滚动到对应行（不打断用户拖动）
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress &&
            state.firstVisibleItemIndex != selectedIndex
        ) {
            state.scrollToItem(selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        }
    }

    // 滚动结束后回报中心项
    LaunchedEffect(state, items) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, offset) ->
                if (!state.isScrollInProgress) {
                    val centerIdx = (idx + if (offset > 0) 1 else 0)
                        .coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    if (centerIdx != selectedIndex) onSelected(centerIdx)
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItemCount)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        // 顶部/底部渐隐分隔线，标识中心区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight),
        ) {
            HorizontalDivider(
                modifier = Modifier.align(Alignment.TopStart),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomStart),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = itemHeight * padCount),
        ) {
            itemsIndexed(items) { idx, label ->
                val isSelected by remember {
                    derivedStateOf {
                        val centerIdx = (state.firstVisibleItemIndex +
                            if (state.firstVisibleItemScrollOffset > 0) 1 else 0)
                        idx == centerIdx
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = if (isSelected)
                            MaterialTheme.typography.titleMedium
                        else
                            MaterialTheme.typography.bodyMedium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 多列滚轮容器，便于把"性别 / 年龄 / 身高 / 体重"放在同一卡片内。
 */
@Composable
fun WheelPickerRow(
    columns: List<WheelColumn>,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleItemCount: Int = 5,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        columns.forEach { col ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    col.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                WheelPicker(
                    items = col.items,
                    selectedIndex = col.selectedIndex,
                    onSelected = col.onSelected,
                    itemHeight = itemHeight,
                    visibleItemCount = visibleItemCount,
                )
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

data class WheelColumn(
    val label: String,
    val items: List<String>,
    val selectedIndex: Int,
    val onSelected: (Int) -> Unit,
)
