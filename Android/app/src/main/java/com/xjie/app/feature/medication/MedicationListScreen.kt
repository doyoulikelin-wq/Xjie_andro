package com.xjie.app.feature.medication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.Medication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    onBack: () -> Unit,
    vm: MedicationViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Medication?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Medication?>(null) }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("我的用药") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showEditor = true }) {
                        Icon(Icons.Filled.Add, "新增")
                    }
                },
            )
        },
    ) { inner ->
        if (state.loading && state.items.isEmpty()) {
            Box(Modifier.padding(inner).fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (state.items.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(inner),
                onAdd = { editing = null; showEditor = true },
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(inner).fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.items, key = { it.id }) { med ->
                    MedicationCard(
                        med = med,
                        onEdit = { editing = med; showEditor = true },
                        onDelete = { pendingDelete = med },
                    )
                }
            }
        }
    }

    if (showEditor) {
        MedicationEditDialog(
            editing = editing,
            onDismiss = { showEditor = false; vm.clearRecognized() },
            vm = vm,
        )
    }

    pendingDelete?.let { med ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除用药") },
            text = { Text("确定要删除 ${med.name} 的提醒吗？") },
            confirmButton = {
                TextButton(onClick = { vm.delete(med); pendingDelete = null }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Box(modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.MedicalServices, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("尚未添加任何用药记录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "拍下药品说明书自动识别，或手动添加。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("添加用药")
            }
        }
    }
}

@Composable
private fun MedicationCard(med: Medication, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MedicalServices, null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(med.name, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
            }
            med.dosage?.takeIf { it.isNotBlank() }?.let {
                Text("剂量：$it", style = MaterialTheme.typography.bodyMedium)
            }
            med.frequency?.takeIf { it.isNotBlank() }?.let {
                Text("用法：$it", style = MaterialTheme.typography.bodyMedium)
            }
            if (med.schedule_times.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        med.schedule_times.joinToString("、"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            med.instructions?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val course = listOfNotNull(med.course_start, med.course_end).joinToString(" 至 ")
            if (course.isNotBlank()) {
                Text("疗程：$course", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
