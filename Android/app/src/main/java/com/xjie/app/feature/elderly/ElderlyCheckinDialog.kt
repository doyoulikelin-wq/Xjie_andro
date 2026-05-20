package com.xjie.app.feature.elderly

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import com.xjie.app.core.model.MoodChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElderlyCheckinDialog(
    vm: ElderlyViewModel,
    source: String,
    onDismiss: () -> Unit,
) {
    var activity by remember { mutableStateOf("") }
    var body by remember { mutableStateOf<BodyFeeling?>(null) }
    var mood by remember { mutableStateOf<MoodChoice?>(null) }
    var note by remember { mutableStateOf("") }
    val submitting = vm.state.collectAsState().value.submitting
    val canSubmit = activity.isNotBlank() || body != null || mood != null || note.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "现在感觉如何？",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionLabel("您正在做什么？")
                ActivityGrid(selected = activity, onSelect = { activity = if (activity == it) "" else it })
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it.take(60) },
                    label = { Text("或输入活动", fontSize = 17.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                )

                SectionLabel("身体感觉")
                EmojiRow(
                    items = BodyFeeling.entries.map { Triple(it.raw, it.emoji, it.label) },
                    selected = body?.raw,
                    onSelect = { raw -> body = BodyFeeling.fromRaw(raw).takeUnless { it == body } },
                )

                SectionLabel("此刻心情")
                EmojiRow(
                    items = MoodChoice.entries.map { Triple(it.raw, it.emoji, it.label) },
                    selected = mood?.raw,
                    onSelect = { raw -> mood = MoodChoice.fromRaw(raw).takeUnless { it == mood } },
                )

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
private fun ActivityGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        COMMON_ACTIVITIES.chunked(4).forEach { row ->
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
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
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
        items.forEach { (raw, emoji, label) ->
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
                    Text(emoji, fontSize = 32.sp, modifier = Modifier.padding(4.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
