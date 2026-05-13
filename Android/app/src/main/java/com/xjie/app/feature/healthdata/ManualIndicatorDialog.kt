package com.xjie.app.feature.healthdata

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualIndicatorDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    vm: ManualIndicatorViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var valueText by remember { mutableStateOf("") }
    var unitText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().toString()) }
    var notesText by remember { mutableStateOf("") }

    LaunchedEffect(state.selected) {
        // 自动填充建议单位
        state.selected?.unit?.takeIf { unitText.isBlank() && it.isNotBlank() }?.let { unitText = it }
    }
    LaunchedEffect(state.savedOk) {
        if (state.savedOk) { onSaved(); onDismiss() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("手动录入指标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 540.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::updateQuery,
                    label = { Text("指标名称（中/英文/拼音/缩写）") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.searching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.selected == null && state.results.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    ) {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(state.results, key = { it.name }) { it ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Surface(
                                        onClick = { vm.select(it) },
                                        color = androidx.compose.ui.graphics.Color.Transparent,
                                    ) {
                                        Column {
                                            Row {
                                                Text(it.name, fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium)
                                                it.category?.takeIf { c -> c.isNotBlank() }?.let { c ->
                                                    Spacer(Modifier.width(6.dp))
                                                    AssistChip(onClick = {}, label = { Text(c, style = MaterialTheme.typography.labelSmall) })
                                                }
                                            }
                                            it.alias?.takeIf { a -> a.isNotBlank() }?.let { a ->
                                                Text(
                                                    "别名：$a",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
                if (state.selected == null && state.query.isNotBlank() && !state.searching && state.results.isEmpty()) {
                    Text(
                        "未匹配到指标，可直接以输入名称保存（按下方按钮）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v -> valueText = v.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(12) },
                    label = { Text("数值") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { unitText = it.take(20) },
                    label = { Text("单位（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.take(10) },
                    label = { Text("测量日期 YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it.take(140) },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.saving,
                onClick = {
                    val v = valueText.toDoubleOrNull() ?: return@TextButton
                    val name = (state.selected?.name ?: state.query).trim()
                    if (name.isBlank()) return@TextButton
                    val date = runCatching { LocalDate.parse(dateText) }.getOrNull()
                        ?: LocalDate.now()
                    val measuredAt = OffsetDateTime.of(date.atTime(12, 0), ZoneOffset.ofHours(8))
                    vm.submit(
                        indicatorName = name,
                        value = v,
                        unit = unitText,
                        measuredAt = measuredAt,
                        notes = notesText,
                    )
                },
            ) { Text(if (state.saving) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = { vm.reset(); onDismiss() }) { Text("取消") }
        },
    )
}
