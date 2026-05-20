package com.xjie.app.feature.elderly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.BodyFeeling
import com.xjie.app.core.model.ElderlyCheckin
import com.xjie.app.core.model.MoodChoice
import com.xjie.app.core.ui.theme.cardStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderlyHistoryScreen(
    onBack: () -> Unit,
    vm: ElderlyViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.loadHistory(); vm.loadStatus(autoPrompt = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("健康签到历史", fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.openSheet("manual") }) {
                        Icon(Icons.Filled.Add, "新增签到")
                    }
                },
            )
        },
    ) { inner ->
        if (state.history.isEmpty()) {
            Box(
                Modifier.padding(inner).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无签到记录",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(inner),
            ) {
                items(state.history, key = { it.id }) { item ->
                    HistoryRow(item = item, onDelete = { vm.delete(item.id) })
                }
            }
        }
    }

    if (state.showSheet) {
        ElderlyCheckinDialog(
            vm = vm,
            source = state.sheetSource,
            onDismiss = { vm.closeSheet() },
        )
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("好") } },
        )
    }
}

@Composable
private fun HistoryRow(item: ElderlyCheckin, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val title = listOfNotNull(
                    item.activity?.takeIf { it.isNotBlank() },
                    BodyFeeling.fromRaw(item.body_feeling)?.let { "${it.emoji} ${it.label}" },
                    MoodChoice.fromRaw(item.mood)?.let { "${it.emoji} ${it.label}" },
                ).joinToString("  ·  ").ifBlank { "（无具体内容）" }
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                item.note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    item.created_at.replace("T", " ").take(16),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
