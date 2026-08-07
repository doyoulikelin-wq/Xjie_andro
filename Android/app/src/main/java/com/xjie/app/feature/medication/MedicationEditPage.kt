package com.xjie.app.feature.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody

/** A real navigation page: this form is too long and stateful to behave like a dismissible dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditPage(
    editing: Medication?,
    onBack: () -> Unit,
    vm: MedicationViewModel,
) {
    val state by vm.state.collectAsState()
    val initialTimes = remember(editing) { editing?.schedule_times.orEmpty() }
    var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
    var dosage by remember(editing) { mutableStateOf(editing?.dosage.orEmpty()) }
    var frequency by remember(editing) { mutableStateOf(editing?.frequency.orEmpty()) }
    var instructions by remember(editing) { mutableStateOf(editing?.instructions.orEmpty()) }
    var times by remember(editing) { mutableStateOf(initialTimes.toMutableList()) }
    var courseStart by remember(editing) { mutableStateOf(editing?.course_start.orEmpty()) }
    var courseEnd by remember(editing) { mutableStateOf(editing?.course_end.orEmpty()) }
    var enabled by remember(editing) { mutableStateOf(editing?.enabled ?: false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var validationError by remember(editing) { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun dismissKeyboard() {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }

    val dirty = name != editing?.name.orEmpty() ||
        dosage != editing?.dosage.orEmpty() ||
        frequency != editing?.frequency.orEmpty() ||
        instructions != editing?.instructions.orEmpty() ||
        times != initialTimes ||
        courseStart != editing?.course_start.orEmpty() ||
        courseEnd != editing?.course_end.orEmpty() ||
        enabled != (editing?.enabled ?: false)

    fun requestBack() {
        dismissKeyboard()
        if (dirty && !state.saving) confirmDiscard = true else onBack()
    }

    fun save() {
        dismissKeyboard()
        val validation = MedicationFormPolicy.validate(times, courseStart, courseEnd)
        if (!validation.isValid) {
            validationError = validation.error
            return
        }
        validationError = null
        vm.save(
            MedicationBody(
                name = name.trim(),
                dosage = dosage.trim().ifBlank { null },
                frequency = frequency.trim().ifBlank { null },
                instructions = instructions.trim().ifBlank { null },
                schedule_times = validation.normalizedTimes,
                course_start = courseStart.trim().ifBlank { null },
                course_end = courseEnd.trim().ifBlank { null },
                enabled = enabled,
            ),
            editing,
            onBack,
        )
    }

    val dismissKeyboardOnScroll = remember(focus, keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): Offset {
                if (available.y != 0f) dismissKeyboard()
                return Offset.Zero
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "新增用药" else "编辑用药") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = ::save,
                        enabled = name.isNotBlank() && !state.saving,
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存")
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .nestedScroll(dismissKeyboardOnScroll)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "请核对药名、用途和疗程后再保存。提醒默认关闭，只会在你主动开启后生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            validationError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("药品名称 *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("剂量（如 10mg、1 片）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = frequency,
                onValueChange = { frequency = it },
                label = { Text("用途与频次（如 控制血压，每日 1 次）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3,
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it },
                label = { Text("服用说明 / 注意事项") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 180.dp),
                minLines = 3,
                maxLines = 6,
            )

            Text("提醒时间", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            times.forEachIndexed { index, time ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { value ->
                            times = times.toMutableList().also { it[index] = value }
                        },
                        label = { Text("HH:MM") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = {
                        times = times.toMutableList().also { it.removeAt(index) }
                    }) { Icon(Icons.Filled.Close, "删除时间点") }
                }
            }
            OutlinedButton(onClick = {
                times = times.toMutableList().also { it += "08:00" }
            }) {
                Icon(Icons.Filled.AddCircle, null)
                Spacer(Modifier.width(6.dp))
                Text("添加时间点")
            }

            OutlinedTextField(
                value = courseStart,
                onValueChange = { courseStart = it },
                label = { Text("开始日期 YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = courseEnd,
                onValueChange = { courseEnd = it },
                label = { Text("结束日期 YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (enabled) "提醒已开启" else "提醒未开启")
                        Text(
                            if (enabled) "系统通知权限关闭时，提醒仍不会出现。" else "保存用药不会自动开启通知。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("放弃未保存修改？") },
            text = { Text("返回后，本页尚未保存的用药信息会丢失。") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") }
            },
        )
    }
}
