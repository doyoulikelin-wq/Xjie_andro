package com.xjie.app.feature.xage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.HealthDocument
import com.xjie.app.core.model.IndicatorTrend
import com.xjie.app.core.model.TrendPoint
import com.xjie.app.core.model.UserProfile
import com.xjie.app.core.network.api.AgentApi
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.UserApi
import com.xjie.app.feature.chat.ChatRepository
import com.xjie.app.feature.elderly.ElderlyRepository
import com.xjie.app.feature.healthdata.HealthDataRepository
import com.xjie.app.feature.healthplan.HealthPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class XAgeServerSyncViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val userApi: UserApi,
    private val dashboardApi: DashboardApi,
    private val agentApi: AgentApi,
    private val healthDataRepository: HealthDataRepository,
    private val chatRepository: ChatRepository,
    private val healthPlanRepository: HealthPlanRepository,
    private val elderlyRepository: ElderlyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(XAgeServerSyncState())
    val state: StateFlow<XAgeServerSyncState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val owner = authManager.captureAccountScope()
        if (owner == null) {
            _state.value = XAgeServerSyncState(snapshot = XAgeServerSyncSnapshot.loggedOut)
            return
        }

        viewModelScope.launch {
            if (!authManager.isCurrent(owner)) return@launch
            _state.update { current ->
                if (current.accountScope != owner.accountScope) {
                    XAgeServerSyncState(
                        isLoading = true,
                        accountScope = owner.accountScope,
                    )
                } else {
                    current.copy(isLoading = true, errorMessage = null)
                }
            }
            runCatching { loadSnapshot(owner) }
                .onSuccess { loaded ->
                    if (loaded == null || !authManager.isCurrent(owner)) return@onSuccess
                    _state.value = loaded.copy(isLoading = false)
                }
                .onFailure { error ->
                    if (!authManager.isCurrent(owner)) return@onFailure
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "历史数据同步失败",
                        )
                    }
                }
        }
    }

    private suspend fun loadSnapshot(
        owner: AuthManager.AccountScopeSnapshot,
    ): XAgeServerSyncState? = coroutineScope {
        val userReq = async { runCatching { userApi.meForOwner(owner) }.getOrNull() }
        val dashboardReq = async { runCatching { dashboardApi.healthForOwner(owner) }.getOrNull() }
        val todayReq = async { runCatching { agentApi.todayForOwner(owner) }.getOrNull() }
        val summaryReq = async { healthDataRepository.summary(owner) }
        val recordsReq = async {
            runCatching { healthDataRepository.documents(owner, "record") }
                .getOrDefault(emptyList())
        }
        val examsReq = async {
            runCatching { healthDataRepository.documents(owner, "exam") }
                .getOrDefault(emptyList())
        }
        val indicatorsReq = async {
            runCatching { healthDataRepository.listIndicators(owner) }
                .getOrDefault(emptyList())
        }
        val watchedReq = async {
            runCatching { healthDataRepository.watchedIndicators(owner) }
                .getOrDefault(emptyList())
        }
        val conversationsReq = async {
            runCatching {
                chatRepository.listConversations(
                    limit = 20,
                    offset = 0,
                    expectedOwner = owner,
                )
            }.getOrDefault(emptyList())
        }
        val plansReq = async {
            runCatching { healthPlanRepository.plans(owner).items }
                .getOrDefault(emptyList())
        }
        val elderlyReq = async {
            runCatching { elderlyRepository.list(owner, days = 30, limit = 100).items }
                .getOrDefault(emptyList())
        }

        val user = userReq.await()
        val dashboard = dashboardReq.await()
        val today = todayReq.await()
        val summary = summaryReq.await()
        val records = recordsReq.await()
        val exams = examsReq.await()
        val indicators = indicatorsReq.await()
        val watched = watchedReq.await()
        val conversations = conversationsReq.await()
        val plans = plansReq.await()
        val elderly = elderlyReq.await()
        if (!authManager.isCurrent(owner)) return@coroutineScope null

        val watchedNames = watched.map { it.indicator_name }.filter { it.isNotBlank() }
        val requestedTrendNames = XAgeHealthTrendRequestContract.names(watchedNames)
        val trends = runCatching { healthDataRepository.trends(owner, requestedTrendNames) }
            .getOrDefault(emptyList())
        if (!authManager.isCurrent(owner)) return@coroutineScope null

        val snapshot = XAgeServerSyncSnapshot(
            isLoaded = true,
            isLoggedOut = false,
            summaryUpdatedAt = summary?.updated_at,
            hasSummary = !summary?.summary_text.isNullOrBlank(),
            recordCount = records.size,
            examCount = exams.size,
            trustedDocumentCount = (records + exams).count {
                it.report_workflow_status?.lowercase() in
                    setOf("completed", "completed_score_pending")
            },
            indicatorCount = indicators.size,
            watchedIndicatorCount = watchedNames.size,
            trendPointCount = trends.sumOf { it.points.size },
            conversationCount = conversations.size,
            planCount = plans.size,
            feedbackCount = elderly.size,
            profileCompletion = profileCompletion(user?.profile),
            latestDocumentDate = latestDocumentDate(records, exams),
            dashboardScore = dashboard?.metabolic_state?.score,
            todayGoalCount = today?.daily_plan?.payload?.today_goals?.size ?: 0,
            primaryWatchedName = watchedNames.firstOrNull(),
            userAge = user?.profile?.age,
            profileHeightCm = user?.profile?.height_cm,
            profileWeightKg = user?.profile?.weight_kg,
            algorithmTrends = algorithmTrends(trends),
        )

        XAgeServerSyncState(
            snapshot = snapshot,
            metricCards = metricCardsFromTrends(trends),
            accountScope = owner.accountScope,
        )
    }

    private fun profileCompletion(profile: UserProfile?): Int {
        if (profile == null) return 0
        val fields = listOf(
            !profile.sex.isNullOrBlank(),
            profile.age != null,
            profile.height_cm != null,
            profile.weight_kg != null,
            !profile.display_name.isNullOrBlank(),
        )
        return ((fields.count { it } / fields.size.toDouble()) * 100).toInt()
    }

    private fun latestDocumentDate(records: List<HealthDocument>, exams: List<HealthDocument>): String? =
        (records + exams).mapNotNull { it.doc_date }.maxOrNull()

    private fun metricCardsFromTrends(trends: List<IndicatorTrend>): List<XAgeServerMetric> {
        val accents = listOf(0xFF238AD6, 0xFF20CDB1, 0xFFEF9A3D, 0xFF7B4DFF)
        val seenIds = mutableSetOf<String>()
        val seenTitles = mutableSetOf<String>()
        return trends
            .filterNot { XAgeMetricIdentityPolicy.isLegacyCombinedBloodPressure(it.name) }
            .mapIndexedNotNull { index, trend ->
            val latest = latestPoint(trend.points) ?: return@mapIndexedNotNull null
            val source = latest.source.orEmpty().lowercase()
            val sourceLabel = when (source) {
                "device", "apple_health" -> "设备同步趋势"
                "manual" -> "手动记录趋势"
                "cgm" -> "CGM 趋势"
                "document" -> "已确认报告趋势"
                else -> "指标趋势"
            }
            XAgeServerMetric(
                id = XAgeMetricIdentityPolicy.canonicalId(trend.name),
                title = trend.name,
                value = latest.display_value?.trim()?.takeIf { it.isNotEmpty() }
                    ?: displayValue(latest.value),
                unit = trend.unit.orEmpty(),
                time = XAgeServerSyncFormat.shortDate(
                    latest.measured_at ?: latest.source_local_date ?: latest.date,
                ),
                subtitle = if (latest.abnormal) {
                    "最近一次结果异常，来自服务端$sourceLabel。"
                } else {
                    "来自服务端$sourceLabel，已同步到当前版本。"
                },
                accentArgb = accents[index % accents.size],
                trend = trend,
            )
        }.filter { metric ->
            seenIds.add(metric.id) && seenTitles.add(XAgeMetricIdentityPolicy.titleKey(metric.title))
        }
    }

    /** Daily scores consume only admitted trend observations, never report csv/abnormal flags. */
    private fun algorithmTrends(trends: List<IndicatorTrend>): List<XAgeAlgorithmTrend> =
        trends.mapNotNull { trend ->
            val latest = latestPoint(
                trend.points.filter {
                    XAgeDailyScoreEvidenceContract.admitsServerSource(it.source.orEmpty())
                },
            ) ?: return@mapNotNull null
            val source = latest.source.orEmpty().lowercase()
            XAgeAlgorithmTrend(
                name = trend.name,
                value = latest.value,
                unit = trend.unit,
                refLow = trend.ref_low,
                refHigh = trend.ref_high,
                abnormal = latest.abnormal,
                measuredAt = latest.measured_at ?: latest.source_local_date ?: latest.date,
                source = source,
                confidence = if (trend.points.size >= 2) 0.82 else 0.72,
                displayValue = latest.display_value,
            )
        }

    private fun latestPoint(points: List<TrendPoint>): TrendPoint? = points.maxWithOrNull(
        compareBy<TrendPoint> {
            XAgeServerSyncFormat.instant(
                it.measured_at ?: it.source_local_date ?: it.date,
            ) ?: Instant.MIN
        }.thenBy { "${it.source.orEmpty()}|${it.source_id.orEmpty()}|${it.value}" },
    )

    private fun displayValue(value: Double): String {
        if (value % 1.0 == 0.0) return value.toInt().toString()
        val digits = if (kotlin.math.abs(value) >= 100) 1 else 2
        return "%.${digits}f".format(value).trimEnd('0').trimEnd('.')
    }
}

data class XAgeServerSyncState(
    val snapshot: XAgeServerSyncSnapshot = XAgeServerSyncSnapshot.placeholder,
    val metricCards: List<XAgeServerMetric> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val accountScope: String? = null,
)

data class XAgeServerMetric(
    val id: String,
    val title: String,
    val value: String,
    val unit: String,
    val time: String,
    val subtitle: String,
    val accentArgb: Long,
    val trend: IndicatorTrend,
)

data class XAgeServerSyncSnapshot(
    val isLoaded: Boolean,
    val isLoggedOut: Boolean,
    val summaryUpdatedAt: String?,
    val hasSummary: Boolean,
    val recordCount: Int,
    val examCount: Int,
    val trustedDocumentCount: Int,
    val indicatorCount: Int,
    val watchedIndicatorCount: Int,
    val trendPointCount: Int,
    val conversationCount: Int,
    val planCount: Int,
    val feedbackCount: Int,
    val profileCompletion: Int,
    val latestDocumentDate: String?,
    val dashboardScore: Int?,
    val todayGoalCount: Int,
    val primaryWatchedName: String?,
    val userAge: Int?,
    val profileHeightCm: Double?,
    val profileWeightKg: Double?,
    val algorithmTrends: List<XAgeAlgorithmTrend>,
) {
    val headerCaption: String
        get() {
            if (!isLoaded) return "正在同步历史数据"
            if (isLoggedOut) return "未登录 · 暂无同步数据"
            if (recordCount + examCount + indicatorCount == 0) return "暂无同步数据"
            return "${XAgeServerSyncFormat.shortDate(summaryUpdatedAt ?: latestDocumentDate)} · 已同步"
        }

    val latestDocumentLabel: String
        get() = XAgeServerSyncFormat.shortDate(latestDocumentDate)

    val primaryWatchedLabel: String
        get() = primaryWatchedName ?: "关注指标"

    companion object {
        val placeholder = XAgeServerSyncSnapshot(
            isLoaded = false,
            isLoggedOut = false,
            summaryUpdatedAt = null,
            hasSummary = false,
            recordCount = 0,
            examCount = 0,
            trustedDocumentCount = 0,
            indicatorCount = 0,
            watchedIndicatorCount = 0,
            trendPointCount = 0,
            conversationCount = 0,
            planCount = 0,
            feedbackCount = 0,
            profileCompletion = 0,
            latestDocumentDate = null,
            dashboardScore = null,
            todayGoalCount = 0,
            primaryWatchedName = null,
            userAge = null,
            profileHeightCm = null,
            profileWeightKg = null,
            algorithmTrends = emptyList(),
        )

        val loggedOut = placeholder.copy(isLoaded = true, isLoggedOut = true)
    }
}

object XAgeServerSyncFormat {
    fun instant(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching {
                LocalDate.parse(value.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC)
            }.getOrNull()
    }

    fun shortDate(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return "暂无"
        val parsedDate = runCatching { OffsetDateTime.parse(value).toLocalDate() }
            .getOrNull()
            ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
        return if (parsedDate != null) {
            "${parsedDate.monthValue}月${parsedDate.dayOfMonth}日"
        } else {
            value.take(10)
        }
    }
}
