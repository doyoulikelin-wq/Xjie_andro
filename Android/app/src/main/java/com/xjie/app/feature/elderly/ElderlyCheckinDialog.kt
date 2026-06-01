package com.xjie.app.feature.elderly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjie.app.core.model.BodyFeeling
import com.xjie.app.core.model.COMMON_ACTIVITIES
import com.xjie.app.core.model.ElderlyCheckinKind
import com.xjie.app.core.model.MoodChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderlyCheckinDialog(
    vm: ElderlyViewModel,
    source: String,
    onDismiss: () -> Unit,
    initialActivity: String? = null,
    kind: ElderlyCheckinKind = ElderlyCheckinKind.COMBINED,
) {
    var activity by remember { mutableStateOf(initialActivity ?: "") }
    var body by remember { mutableStateOf<BodyFeeling?>(null) }
    var mood by remember { mutableStateOf<MoodChoice?>(null) }
    var note by remember { mutableStateOf("") }
    val submitting = vm.state.collectAsState().value.submitting
    val canSubmit = activity.isNotBlank() || body != null || mood != null || note.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    kind.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    kind.subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel(kind.activitySection)
                ActivityGrid(
                    options = kind.options,
                    selected = activity,
                    onSelect = { activity = if (activity == it) "" else it },
                )
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it.take(60) },
                    label = { Text("或输入其他...", fontSize = 17.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                )

                if (kind.showBodyFeeling) {
                    SectionLabel(if (kind == ElderlyCheckinKind.MEDICATION) "服药后身体感觉" else "身体感觉")
                    EmojiRow(
                        items = BodyFeeling.entries.map { Triple(it.raw, it.emoji, it.label) },
                        selected = body?.raw,
                        onSelect = { raw -> body = BodyFeeling.fromRaw(raw).takeUnless { it == body } },
                    )
                }

                if (kind.showMood) {
                    SectionLabel(if (kind == ElderlyCheckinKind.SLEEP) "醒来后心情" else "此刻心情")
                    EmojiRow(
                        items = MoodChoice.entries.map { Triple(it.raw, it.emoji, it.label) },
                        selected = mood?.raw,
                        onSelect = { raw -> mood = MoodChoice.fromRaw(raw).takeUnless { it == mood } },
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("想说点什么（可选）", fontSize = 17.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.submit(
                        activity = activity,
                        bodyFeeling = body?.raw,
                        mood = mood?.raw,
                        note = note,
                        source = source,
                        promptType = kind.apiValue,
                    )
                },
                enabled = canSubmit && !submitting,
            ) {
                Text(if (submitting) "提交中…" else "提交", fontSize = 18.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("以后再说", fontSize = 18.sp) }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ActivityGrid(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    val isSel = selected == label
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        onClick = { onSelect(label) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                fontSize = 16.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun EmojiRow(
    items: List<Triple<String, String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEach { (raw, _, label) ->
            val isSel = selected == raw
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .padding(vertical = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    onClick = { onSelect(raw) },
                    color = Color.Transparent,
                ) {
                    Text(label, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                }
            }
        }
    }
}
