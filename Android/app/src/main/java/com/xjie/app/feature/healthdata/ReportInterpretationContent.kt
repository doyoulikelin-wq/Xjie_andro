package com.xjie.app.feature.healthdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xjie.app.core.model.HealthReportInterpretation
import com.xjie.app.core.ui.theme.XjiePalette
import com.xjie.app.core.ui.theme.cardStyle

@Composable
internal fun ReportInterpretationContent(
    state: DocumentDetailViewModel.UiState,
    onRetry: () -> Unit,
    onOpenOriginal: () -> Unit,
) {
    when {
        state.interpretationLoading && state.interpretation == null -> {
            InterpretationCard("正在读取本次解读") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(10.dp))
                    Text("正在读取已确认字段、来源和评分快照…")
                }
            }
        }
        state.interpretation == null -> {
            InterpretationCard("暂时无法读取") {
                Text(
                    HealthReportReleasePresentation.transientError(
                        fallback = "尚未读取到本次解读，请稍后重试。",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("重新读取本次解读") }
            }
        }
        else -> InterpretationBody(
            interpretation = state.interpretation,
            hasLocalOriginalCandidate = state.authoritativeWorkflowId != null,
            originalLoading = state.originalLoading,
            interpretationError = state.interpretationError,
            onOpenOriginal = onOpenOriginal,
        )
    }
}

@Composable
private fun InterpretationBody(
    interpretation: HealthReportInterpretation,
    hasLocalOriginalCandidate: Boolean,
    originalLoading: Boolean,
    interpretationError: String?,
    onOpenOriginal: () -> Unit,
) {
    InterpretationCard("解读边界") {
        Text(
            HealthReportReleasePresentation.notice(interpretation.non_diagnostic_notice),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "只展示已确认的结构化数据和服务端实际记录；没有证据的影响不会补写。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!interpretation.available) {
        InterpretationCard("解读尚不可用") {
            Text(
                HealthReportReleasePresentation.unavailableReason(
                    interpretation.unavailable_reason,
                    fallback = "报告尚未完成确认。",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    InterpretationCard("已确认的异常项") {
        if (interpretation.major_abnormalities.isEmpty()) {
            Text(
                "本次已确认字段中，没有服务端标记为异常的项目。这不等同于排除其他健康问题。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            interpretation.major_abnormalities.forEach { observation ->
                ObservationRow(HealthReportReleasePresentation.observation(observation))
            }
        }
    }

    InterpretationCard("随访与复查信息") {
        val followUpItems = HealthReportReleasePresentation.followUpItems(
            interpretation.follow_up.items,
        )
        if (interpretation.follow_up.available && followUpItems.isNotEmpty()) {
            followUpItems.forEach { Text("• $it") }
        } else {
            Text(
                HealthReportReleasePresentation.unavailableReason(
                    interpretation.follow_up.unavailable_reason,
                    fallback = "没有经过确认的随访信息。",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    InterpretationCard("压力、恢复与炎症评分") {
        Text(
            ReportTrustPresentation.scoreHeadline(interpretation),
            fontWeight = FontWeight.SemiBold,
            color = if (interpretation.score_pending) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (interpretation.score_snapshots.isEmpty()) {
            Text(
                "当前没有可展示的服务端评分快照，因此不会显示虚构的分数变化。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            interpretation.score_snapshots.forEach { snapshot ->
                ScoreSnapshotRow(HealthReportReleasePresentation.score(snapshot))
            }
        }
    }

    InterpretationCard("健康画像候选") {
        val impactGroups = interpretation.profile_impacts
            .groupBy { it.profile_candidate_id }
            .toSortedMap()
        if (impactGroups.isEmpty()) {
            Text(
                "本次报告没有生成可追溯的画像候选；系统不会据此宣称画像已改变。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            impactGroups.values.forEach { impacts ->
                val presentation = HealthReportReleasePresentation.profileCandidate(impacts)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            presentation.title,
                            Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            presentation.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        presentation.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        presentation.evidenceSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
            Text(
                "未接受的候选不代表画像事实已经更新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    InterpretationCard("本次加入的结构化数据") {
        if (interpretation.structured_additions.isEmpty()) {
            Text("没有处于有效状态的已确认观测。")
        } else {
            interpretation.structured_additions.forEach { observation ->
                ObservationRow(HealthReportReleasePresentation.observation(observation))
            }
        }
    }

    InterpretationCard("识别、修正与确认记录") {
        interpretation.candidates.forEach { candidate ->
            CandidateProvenanceRow(HealthReportReleasePresentation.candidate(candidate))
        }
        if (interpretation.confirmation_events.isNotEmpty()) {
            HorizontalDivider()
            Text("不可变确认事件", fontWeight = FontWeight.SemiBold)
            interpretation.confirmation_events.forEach { event ->
                ConfirmationEventRow(HealthReportReleasePresentation.confirmation(event))
            }
        }
    }

    InterpretationCard("原始报告") {
        val hasServerOriginal = !interpretation.document?.file_url.isNullOrBlank()
        val hasOriginal = hasLocalOriginalCandidate || hasServerOriginal
        Text(
            if (hasLocalOriginalCandidate) {
                "优先读取账号隔离且完整性已校验的本机原件；缺失时再尝试服务器备份。"
            } else if (hasServerOriginal) {
                "本机原件不可用，将尝试使用登录态安全读取服务器备份。"
            } else {
                "当前没有可访问的本机原件或服务器备份；已确认字段和来源记录仍保留。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasOriginal) {
            OutlinedButton(
                onClick = onOpenOriginal,
                enabled = !originalLoading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (originalLoading) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (originalLoading) "正在读取原件…" else "打开原始报告")
            }
        }
        interpretationError?.let {
            Text(
                HealthReportReleasePresentation.transientError(),
                style = MaterialTheme.typography.bodySmall,
                color = XjiePalette.Danger,
            )
        }
    }
}

@Composable
private fun InterpretationCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.cardStyle(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun ObservationRow(
    observation: HealthReportReleasePresentation.Observation,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(observation.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                observation.status,
                color = if (observation.status == "异常") {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    XjiePalette.Success
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(observation.value, fontWeight = FontWeight.SemiBold)
        Text(
            "参考：${observation.reference}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            observation.provenance,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ScoreSnapshotRow(snapshot: HealthReportReleasePresentation.Score) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(snapshot.kindTitle, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                snapshot.status,
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot.status == "已完成") {
                    XjiePalette.Success
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
        }
        Text(snapshot.value, fontWeight = FontWeight.SemiBold)
        snapshot.semanticSummary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        snapshot.confidenceSummary?.let { confidence ->
            Text(
                confidence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        snapshot.directionSummary?.let { direction ->
            Text(
                direction,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        snapshot.evidenceSummary?.let {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
        snapshot.missingInputsSummary?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        snapshot.failureSummary?.let {
            Text(it, style = MaterialTheme.typography.labelSmall)
        }
    }
    HorizontalDivider()
}

@Composable
private fun CandidateProvenanceRow(candidate: HealthReportReleasePresentation.Candidate) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(candidate.title, fontWeight = FontWeight.SemiBold)
        Text(
            "原始：${candidate.originalValue}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "确认后：${candidate.confirmedValue} · ${candidate.status}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

@Composable
private fun ConfirmationEventRow(event: HealthReportReleasePresentation.Confirmation) {
    Text(
        "${event.title} · ${event.change}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
