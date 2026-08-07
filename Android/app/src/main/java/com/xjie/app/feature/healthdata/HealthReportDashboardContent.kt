package com.xjie.app.feature.healthdata

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjie.app.core.ui.theme.XjiePalette

/** One report dashboard with one and only one primary upload action. */
@Composable
internal fun HealthReportDashboardContent(
    state: HealthReportDashboardState,
    onUpload: () -> Unit,
    onOpenReport: (Long) -> Unit,
    onRefresh: () -> Unit,
    onRecoverAsset: (Int) -> Unit,
    onAbandonRecovery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("health.report.dashboard.root"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "健康报告",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "上传、确认并查看可信报告记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("health.report.dashboard.refresh"),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("刷新")
            }
        }

        when (state.contentState) {
            HealthReportDashboardContentState.Loading -> LoadingReportCard()
            HealthReportDashboardContentState.Error -> ErrorReportCard(onRefresh)
            HealthReportDashboardContentState.Empty -> EmptyReportCard()
            HealthReportDashboardContentState.Available -> AvailableReportCard(
                state = state,
                onOpenReport = onOpenReport,
                onRefresh = onRefresh,
            )
        }

        UploadLifecycleCard(
            upload = state.upload,
            authoritativeWorkflowId = state.latestItem?.workflowId,
            onRecoverAsset = onRecoverAsset,
            onAbandonRecovery = onAbandonRecovery,
        )

        Button(
            onClick = onUpload,
            enabled = state.upload !is HealthReportDashboardUploadState.Submitting,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("health.report.primaryUpload"),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("上传新报告", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoadingReportCard() {
    DashboardCard("health.report.state.loading") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("正在读取最新报告…", fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ErrorReportCard(onRefresh: () -> Unit) {
    DashboardCard("health.report.state.error") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = XjiePalette.Danger)
            Spacer(Modifier.width(8.dp))
            Text("健康报告暂时无法读取", fontWeight = FontWeight.SemiBold)
        }
        Text(
            HealthReportReleasePresentation.transientError(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("重新读取")
        }
    }
}

@Composable
private fun EmptyReportCard() {
    DashboardCard("health.report.state.empty") {
        Text("暂无健康报告", fontWeight = FontWeight.SemiBold)
        Text(
            "上传报告后，这里会显示最新解析状态和近一年记录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AvailableReportCard(
    state: HealthReportDashboardState,
    onOpenReport: (Long) -> Unit,
    onRefresh: () -> Unit,
) {
    val presentation = state.presentation
    DashboardCard("health.report.state.${presentation.phaseTag}") {
        state.latestItem?.let { item ->
            Text(
                HealthReportReleasePresentation.reportTitle(item.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            safeHistoryMetadata(item)?.let { metadata ->
                Text(
                    metadata,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (state.phase in setOf(
                        HealthReportDashboardPhase.Completed,
                        HealthReportDashboardPhase.ScorePending,
                    )
                ) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.ErrorOutline
                },
                contentDescription = null,
                tint = when (state.phase) {
                    HealthReportDashboardPhase.Completed -> XjiePalette.Success
                    HealthReportDashboardPhase.ScorePending -> MaterialTheme.colorScheme.tertiary
                    HealthReportDashboardPhase.Recoverable,
                    HealthReportDashboardPhase.Failed,
                    -> XjiePalette.Danger
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(presentation.title, fontWeight = FontWeight.SemiBold)
        }
        Text(
            presentation.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.displayedRuntime?.failureCode?.let { code ->
            Text(
                HealthReportReleasePresentation.failureMessage(code),
                style = MaterialTheme.typography.bodySmall,
                color = XjiePalette.Danger,
            )
        }
        state.detailWarning?.let {
            Text(
                "报告详情暂时无法读取，下拉即可重试。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        state.readError?.let {
            Text(
                "健康报告刷新失败，当前显示上次成功读取的记录。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("重新读取")
            }
        }
        state.latestItem?.let { latest ->
            OutlinedButton(
                onClick = { onOpenReport(latest.workflowId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("health.report.latestAction"),
            ) {
                Text(presentation.reportActionTitle)
            }
        }
        if (state.recentItems.size > 1) {
            Text("近一年报告", fontWeight = FontWeight.SemiBold)
            state.recentItems.drop(1).forEach { item ->
                OutlinedButton(
                    onClick = { onOpenReport(item.workflowId) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(HealthReportReleasePresentation.reportTitle(item.title))
                }
            }
        }
    }
}

@Composable
private fun UploadLifecycleCard(
    upload: HealthReportDashboardUploadState,
    authoritativeWorkflowId: Long?,
    onRecoverAsset: (Int) -> Unit,
    onAbandonRecovery: () -> Unit,
) {
    when (upload) {
        HealthReportDashboardUploadState.Idle -> Unit
        HealthReportDashboardUploadState.Submitting -> DashboardCard(
            "health.report.upload.submitting",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在安全保存并上传报告原件…", fontWeight = FontWeight.SemiBold)
            }
            Text("本机保存失败时不会创建网络上传会话。", style = MaterialTheme.typography.bodySmall)
        }
        is HealthReportDashboardUploadState.RecoveryRequired -> DashboardCard(
            "health.report.upload.recovery",
        ) {
            Text("报告需要补充处理", fontWeight = FontWeight.SemiBold)
            Text(HealthReportReleasePresentation.failureMessage(upload.recovery.failureCode))
            upload.recovery.nextAssetIndex?.let { Text("请检查第 $it 页原件后继续。") }
            upload.recovery.nextAssetIndex?.let { assetIndex ->
                OutlinedButton(
                    onClick = { onRecoverAsset(assetIndex) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("选择第 $assetIndex 页替换文件")
                }
            }
            OutlinedButton(
                onClick = onAbandonRecovery,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("放弃本次上传")
            }
        }
        is HealthReportDashboardUploadState.Completed -> DashboardCard(
            "health.report.upload.completed",
        ) {
            val pendingPresentation = upload.runtime
                .takeIf { it.workflowId != authoritativeWorkflowId }
                ?.let { runtime ->
                    HealthReportDashboardPolicy.presentation(
                        HealthReportDashboardPolicy.phase(
                            workflowStatus = runtime.workflowStatus,
                            failureCode = runtime.failureCode,
                        ),
                    )
                }
            Text(
                if (upload.duplicate) "已打开原有报告记录" else "报告原件已安全保存",
                fontWeight = FontWeight.SemiBold,
            )
            pendingPresentation?.let { presentation ->
                Text(presentation.title, fontWeight = FontWeight.SemiBold)
                Text(presentation.summary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (upload.acknowledgementDeferred) {
                    "本机证明将在联网后重试；服务器副本会继续保留。"
                } else {
                    "可继续查看最新处理状态。"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        is HealthReportDashboardUploadState.Failed -> DashboardCard(
            "health.report.upload.failed",
        ) {
            Text("报告上传未完成", fontWeight = FontWeight.SemiBold)
            Text(
                HealthReportReleasePresentation.userMessage(
                    upload.message,
                    fallback = "报告上传未完成，请检查网络后重试。",
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DashboardCard(
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

private fun safeHistoryMetadata(item: HealthReportHistoryItem): String? {
    val date = item.reportDate?.takeIf { REPORT_DATE.matches(it) }
        ?: item.createdAt.take(10).takeIf { REPORT_DATE.matches(it) }
    val hospital = HealthReportReleasePresentation.userMessage(item.hospital, fallback = "")
        .takeIf(String::isNotBlank)
    return listOfNotNull(date, hospital).joinToString(" · ").takeIf(String::isNotBlank)
}

private val REPORT_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
