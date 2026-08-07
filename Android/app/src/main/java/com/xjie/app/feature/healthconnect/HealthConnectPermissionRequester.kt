package com.xjie.app.feature.healthconnect

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjie.app.core.quality.UiAutomationRuntime

/**
 * Place this once beside the screen that owns [HealthConnectSyncViewModel], then invoke
 * `viewModel.requestSync()` from an explicit button. The bridge launches the official Health
 * Connect permission contract and routes its result back for an authoritative SDK re-check.
 */
@Composable
fun HealthConnectPermissionRequester(viewModel: HealthConnectSyncViewModel) {
    if (UiAutomationRuntime.isActive) return
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val launcher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = viewModel::onPermissionResult,
    )
    val request = state.permissionRequest
    LaunchedEffect(request?.id) {
        request ?: return@LaunchedEffect
        viewModel.consumePermissionRequest(request.id)?.let(launcher::launch)
    }
}
