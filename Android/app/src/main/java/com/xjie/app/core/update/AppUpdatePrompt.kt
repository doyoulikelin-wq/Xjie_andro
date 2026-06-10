package com.xjie.app.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.xjie.app.core.model.AppUpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.security.MessageDigest

@Composable
fun AppUpdatePrompt(vm: AppUpdateViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = state.pendingUpdate

    LaunchedEffect(Unit) {
        vm.checkIfNeeded()
    }

    if (info != null) {
        AlertDialog(
            onDismissRequest = {
                if (!info.shouldForce) vm.dismiss(info)
            },
            title = { Text(info.title) },
            text = {
                val parts = listOf(
                    "最新版本：${info.latestVersion}(${info.latestBuild})",
                    info.message,
                    info.changelog,
                    state.error.orEmpty(),
                ).filter { it.isNotBlank() }
                Text(parts.joinToString("\n\n"))
            },
            confirmButton = {
                TextButton(
                    enabled = !state.installing,
                    onClick = {
                        scope.launch {
                            runCatching {
                                ensureInstallPermission(context)
                                vm.setInstalling(true)
                                downloadAndInstallApk(context, info)
                            }.onFailure { e ->
                                vm.setError(e.message ?: "更新失败，请稍后重试")
                            }.onSuccess {
                                vm.setInstalling(false)
                            }
                        }
                    },
                ) {
                    Text(if (state.installing) "下载中" else "立即更新")
                }
            },
            dismissButton = if (info.shouldForce) null else {
                { TextButton(onClick = { vm.dismiss(info) }) { Text("稍后") } }
            },
        )
    }
}

private fun ensureInstallPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        val uri = Uri.parse("package:${context.packageName}")
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        throw IllegalStateException("请先允许小捷安装未知来源应用，返回后再次点击立即更新。")
    }
}

private suspend fun downloadAndInstallApk(context: Context, info: AppUpdateCheck) {
    val rawUrl = info.updateUrl ?: throw IllegalStateException("未配置 Android 下载链接")
    val apkFile = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "Xjie_latest_${info.latestBuild}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        URL(rawUrl).openConnection().apply {
            connectTimeout = 20_000
            readTimeout = 120_000
        }.getInputStream().use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        val expected = info.sha256?.trim().orEmpty()
        if (expected.isNotEmpty()) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            require(actual.equals(expected, ignoreCase = true)) {
                "APK 校验失败，请联系管理员重新发布安装包。"
            }
        }
        file
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        apkFile,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
