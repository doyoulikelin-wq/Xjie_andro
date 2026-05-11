package com.xjie.app.feature.meals

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.MealItem
import com.xjie.app.core.model.MealPhoto
import com.xjie.app.core.ui.components.EmptyState
import com.xjie.app.core.ui.components.LoadingIndicator
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle
import com.xjie.app.core.util.DateUtils
import com.xjie.app.core.util.NumberUtils
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    onBack: (() -> Unit)? = null,
    vm: MealsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPickerSheet by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { vm.uploadPhoto(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) vm.uploadPhoto(uri)
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "meal_photos").apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchCamera()
        else vm.showError("需要相机权限才能拍照记录。")
    }

    fun onPickCamera() {
        showPickerSheet = false
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) { vm.fetchData() }
    LaunchedEffect(state.toast) {
        state.toast?.let { snackbar.showSnackbar(it); vm.clearToast() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("记录膳食") },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !state.uploading,
                    onClick = { showPickerSheet = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(XjiePalette.GradientStart, XjiePalette.GradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PhotoCamera, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("拍照记录", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                OutlinedButton(
                    onClick = vm::openManualInput,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(6.dp))
                    Text("手动记录")
                }
            }

            if (state.uploading) LoadingIndicator(label = "上传中...")

            when {
                state.loading -> LoadingIndicator()
                state.meals.isEmpty() ->
                    EmptyState("\u6682\u65e0\u81b3\u98df\u8bb0\u5f55", description = "\u70b9\u51fb\u4e0a\u65b9\u6309\u94ae\u5f00\u59cb\u8bb0\u5f55")
                else -> MealsSection(
                    meals = state.meals,
                    onDelete = vm::deleteMeal,
                    onUpdate = vm::updateMeal,
                )
            }
        }
    }

    if (state.showManualInput) {
        ManualInputDialog(
            value = state.manualKcal,
            onChange = vm::setManualKcal,
            onConfirm = vm::submitManual,
            onDismiss = vm::dismissManualInput,
        )
    }

    if (showPickerSheet) {
        AlertDialog(
            onDismissRequest = { showPickerSheet = false },
            title = { Text("添加餐饮照片") },
            text = { Text("请选择获取照片的方式") },
            confirmButton = {
                TextButton(onClick = { onPickCamera() }) { Text("拍照") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPickerSheet = false
                    galleryPicker.launch("image/*")
                }) { Text("从相册选择") }
            },
        )
    }
}

@Composable
private fun MealsSection(
    meals: List<MealItem>,
    onDelete: (String) -> Unit,
    onUpdate: (String, Int?, String?) -> Unit,
) {
    var editing by remember { mutableStateOf<MealItem?>(null) }
    var deleting by remember { mutableStateOf<MealItem?>(null) }

    Column(Modifier.cardStyle(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("\u81b3\u98df\u8bb0\u5f55", style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        meals.forEach { m ->
            var menuOpen by remember(m.id) { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${m.kcal?.let { NumberUtils.toFixed(it, 0) } ?: "--"} kcal",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        m.tags?.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                Text(tag, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row {
                        Text(DateUtils.formatDateTime(m.meal_ts),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!m.notes.isNullOrBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(m.notes!!, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "\u66f4\u591a")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("\u7f16\u8f91") },
                            onClick = { menuOpen = false; editing = m },
                        )
                        DropdownMenuItem(
                            text = { Text("\u5220\u9664", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; deleting = m },
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }

    editing?.let { meal ->
        EditMealDialog(
            meal = meal,
            onDismiss = { editing = null },
            onConfirm = { kcal, notes ->
                meal.id?.let { onUpdate(it, kcal, notes) }
                editing = null
            },
        )
    }

    deleting?.let { meal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("\u5220\u9664\u8bb0\u5f55") },
            text = { Text("\u786e\u5b9a\u5220\u9664\u8fd9\u6761\u81b3\u98df\u8bb0\u5f55\u5417\uff1f") },
            confirmButton = {
                TextButton(
                    onClick = { meal.id?.let(onDelete); deleting = null },
                ) { Text("\u5220\u9664", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("\u53d6\u6d88") } },
        )
    }
}

@Composable
private fun EditMealDialog(
    meal: MealItem,
    onDismiss: () -> Unit,
    onConfirm: (Int?, String?) -> Unit,
) {
    var kcalText by remember { mutableStateOf(meal.kcal?.toInt()?.toString() ?: "") }
    var notesText by remember { mutableStateOf(meal.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u7f16\u8f91\u81b3\u98df") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = it.filter { c -> c.isDigit() } },
                    label = { Text("\u70ed\u91cf (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("\u5907\u6ce8") },
                    singleLine = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val k = kcalText.toIntOrNull()
                onConfirm(k, notesText.ifBlank { null })
            }) { Text("\u4fdd\u5b58") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } },
    )
}

@Composable
private fun ManualInputDialog(
    value: String,
    onChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录膳食") },
        text = {
            Column {
                Text("请输入估算热量", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onChange,
                    placeholder = { Text("kcal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("添加") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
