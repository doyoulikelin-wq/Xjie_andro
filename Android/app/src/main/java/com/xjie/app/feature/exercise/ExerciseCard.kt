package com.xjie.app.feature.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.ExerciseBody
import com.xjie.app.core.model.ExerciseItem
import com.xjie.app.core.ui.theme.cardStyle

private val ACTIVITY_OPTIONS: List<Pair<String, String>> = listOf(
    "walking" to "走路",
    "running" to "跑步",
    "cycling" to "骑行",
    "swimming" to "游泳",
    "yoga" to "瑜伽",
    "strength" to "力量训练",
    "hiit" to "HIIT",
    "stretching" to "拉伸",
    "dancing" to "跳舞",
    "ball" to "球类",
    "hiking" to "徒步",
    "other" to "其他",
)

private val INTENSITY_OPTIONS: List<Pair<String, String>> = listOf(
    "low" to "低强度",
    "medium" to "中强度",
    "high" to "高强度",
)

private fun activityLabel(code: String): String =
    ACTIVITY_OPTIONS.firstOrNull { it.first == code }?.second ?: code

private fun intensityLabel(code: String?): String =
    INTENSITY_OPTIONS.firstOrNull { it.first == code }?.second ?: (code ?: "")

@Composable
fun ExerciseCard(
    vm: ExerciseViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.cardStyle()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DirectionsRun, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("今日锻炼", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = { vm.showAdd(true) }) {
                Icon(Icons.Default.Add, "添加锻炼")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.totalMinutes} 分钟",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "约 ${state.totalKcal.toInt()} kcal · ${state.items.size} 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.items.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.items.take(5).forEach { it ->
                    ExerciseRow(it, onDelete = { vm.delete(it.id) })
                }
                if (state.items.size > 5) {
                    Text(
                        "等 ${state.items.size} 项…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (!state.loading) {
            Spacer(Modifier.height(6.dp))
            Text(
                "今天还没有锻炼记录，点击右上角“+”添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (state.showAdd) {
        AddExerciseDialog(
            onDismiss = { vm.showAdd(false) },
            onSave = { vm.add(it) },
        )
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            title = { Text("操作失败") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("好") } },
        )
    }
}

@Composable
private fun ExerciseRow(item: ExerciseItem, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(activityLabel(item.activity_type), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(8.dp))
            Text(
                "${item.duration_minutes}分钟" +
                    (item.intensity?.let { " · ${intensityLabel(it)}" } ?: "") +
                    (item.calories_kcal?.let { " · ${it.toInt()}kcal" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onSave: (ExerciseBody) -> Unit,
) {
    var activity by remember { mutableStateOf("walking") }
    var minutes by remember { mutableStateOf("30") }
    var intensity by remember { mutableStateOf<String?>("medium") }
    var notes by remember { mutableStateOf("") }
    var customType by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加锻炼记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("类型", style = MaterialTheme.typography.labelMedium)
                ExposedDropdownBox(
                    label = activityLabel(activity),
                    options = ACTIVITY_OPTIONS,
                    onSelect = { activity = it },
                )
                if (activity == "other") {
                    OutlinedTextField(
                        value = customType,
                        onValueChange = { customType = it.take(40) },
                        label = { Text("自定义项目（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { v -> minutes = v.filter { ch -> ch.isDigit() }.take(3) },
                    label = { Text("时长（分钟）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("强度", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    INTENSITY_OPTIONS.forEach { (code, label) ->
                        FilterChip(
                            selected = intensity == code,
                            onClick = { intensity = code },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(140) },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = minutes.toIntOrNull() ?: 0
                if (m <= 0) return@TextButton
                val mergedNotes = buildString {
                    if (notes.isNotBlank()) append(notes.trim())
                    if (activity == "other" && customType.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append("[自定义:${customType.trim()}]")
                    }
                }.takeIf { it.isNotBlank() }
                onSave(
                    ExerciseBody(
                        activity_type = activity,
                        duration_minutes = m,
                        intensity = intensity,
                        notes = mergedNotes,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdownBox(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, l) ->
                DropdownMenuItem(text = { Text(l) }, onClick = {
                    onSelect(code); expanded = false
                })
            }
        }
    }
}
