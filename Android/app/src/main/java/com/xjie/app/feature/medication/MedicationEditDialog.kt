package com.xjie.app.feature.medication

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditDialog(
    editing: Medication?,
    onDismiss: () -> Unit,
    vm: MedicationViewModel,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(editing) { mutableStateOf(editing?.name.orEmpty()) }
    var dosage by remember(editing) { mutableStateOf(editing?.dosage.orEmpty()) }
    var frequency by remember(editing) { mutableStateOf(editing?.frequency.orEmpty()) }
    var instructions by remember(editing) { mutableStateOf(editing?.instructions.orEmpty()) }
    var times by remember(editing) {
        mutableStateOf(editing?.schedule_times.orEmpty().toMutableList())
    }
    var courseStart by remember(editing) { mutableStateOf(editing?.course_start.orEmpty()) }
    var courseEnd by remember(editing) { mutableStateOf(editing?.course_end.orEmpty()) }
    var enabled by remember(editing) { mutableStateOf(editing?.enabled ?: true) }

    var ocrBanner by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Auto-fill empty fields after recognize success
    LaunchedEffect(state.recognized) {
        state.recognized?.let { r ->
            if (name.isBlank()) r.name?.let { name = it }
            if (dosage.isBlank()) r.dosage?.let { dosage = it }
            if (frequency.isBlank()) r.frequency?.let { frequency = it }
            if (instructions.isBlank()) r.instructions?.let { instructions = it }
            if (times.isEmpty() && r.schedule_times.isNotEmpty()) {
                times = r.schedule_times.toMutableList()
            }
            ocrBanner = "已自动填充，请核对"
        }
    }
    LaunchedEffect(state.recognizeError) {
        state.recognizeError?.let { ocrBanner = "结构化失败：$it" }
    }

    fun runOcrOnUri(uri: Uri) {
        scope.launch {
            runCatching {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(
                    ChineseTextRecognizerOptions.Builder().build()
                )
                val result = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                    recognizer.process(image)
                        .addOnSuccessListener { t -> cont.resume(t.text, null) }
                        .addOnFailureListener { cont.resume(null, null) }
                }
                result
            }.onSuccess { txt ->
                if (txt.isNullOrBlank()) {
                    ocrBanner = "未识别到文字，请重新拍摄或手动填写"
                } else {
                    vm.recognize(txt)
                }
            }.onFailure {
                ocrBanner = "图片处理失败：${it.message ?: "未知错误"}"
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { runOcrOnUri(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) runOcrOnUri(uri)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "med_photos").apply { mkdirs() }
        val file = File(dir, "med_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() else ocrBanner = "需要相机权限才能拍照识别" }

    fun onPickCamera() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (editing == null) "新增用药" else "编辑用药",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "关闭") }
                }
                Spacer(Modifier.height(8.dp))

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onPickCamera() },
                            modifier = Modifier.weight(1f),
                            enabled = !state.recognizing,
                        ) {
                            Icon(Icons.Filled.CameraAlt, null)
                            Spacer(Modifier.width(6.dp))
                            Text("拍照识别")
                        }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            enabled = !state.recognizing,
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null)
                            Spacer(Modifier.width(6.dp))
                            Text("从相册选择")
                        }
                    }
                    if (state.recognizing) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在识别…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    ocrBanner?.let {
                        AssistChip(onClick = { ocrBanner = null }, label = { Text(it) })
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
                        label = { Text("频次（如 每日3次）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("服用说明 / 注意事项") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )

                    Text("提醒时间", style = MaterialTheme.typography.titleSmall)
                    times.forEachIndexed { idx, t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = t,
                                onValueChange = { newV ->
                                    times = times.toMutableList().also { it[idx] = newV }
                                },
                                label = { Text("HH:MM") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            IconButton(onClick = {
                                times = times.toMutableList().also { it.removeAt(idx) }
                            }) { Icon(Icons.Filled.Close, "删除") }
                        }
                    }
                    OutlinedButton(onClick = {
                        times = times.toMutableList().also { it += "08:00" }
                    }) {
                        Icon(Icons.Filled.AddCircle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("添加时间点")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = courseStart,
                            onValueChange = { courseStart = it },
                            label = { Text("起始日期 YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = courseEnd,
                            onValueChange = { courseEnd = it },
                            label = { Text("结束日期 YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                        Spacer(Modifier.width(8.dp))
                        Text(if (enabled) "已启用提醒" else "已暂停提醒")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            val cleaned = times.map { it.trim() }
                                .filter { it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) }
                            val body = MedicationBody(
                                name = name.trim(),
                                dosage = dosage.trim().ifBlank { null },
                                frequency = frequency.trim().ifBlank { null },
                                instructions = instructions.trim().ifBlank { null },
                                schedule_times = cleaned,
                                course_start = courseStart.trim().ifBlank { null },
                                course_end = courseEnd.trim().ifBlank { null },
                                enabled = enabled,
                            )
                            vm.save(body, editing) { onDismiss() }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank() && !state.saving,
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else Text("保存")
                    }
                }
            }
        }
    }
}
