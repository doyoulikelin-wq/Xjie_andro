package com.xjie.app.core.quality

import com.xjie.app.core.auth.AuthManager
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okio.Buffer
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Debug-only deterministic transport and request ledger. This file is absent from Release. */
object DebugUiAutomationTransport {
    private val lock = Any()
    private val requests = mutableListOf<String>()
    private val unknownRequests = mutableListOf<String>()
    @Volatile private var enabled = false

    @JvmStatic
    fun activate() {
        synchronized(lock) {
            requests.clear()
            unknownRequests.clear()
            enabled = true
        }
    }

    @JvmStatic
    fun bootstrapAuth(authManager: AuthManager, authenticated: Boolean) {
        check(enabled) { "deterministic UI transport was not explicitly activated" }
        if (!authenticated) {
            authManager.logout()
            return
        }
        authManager.establishSession(
            accessToken = fakeJwt(subject = "7"),
            refreshToken = "debug-ui-refresh-token",
            subjectId = "7",
        )
    }

    @JvmStatic
    fun installOn(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        check(enabled) { "deterministic UI transport was not explicitly activated" }
        return builder.addInterceptor(Interceptor { chain ->
            val request = chain.request()
            val key = "${request.method} ${request.url.encodedPath}"
            val fixture = fixture(request)
            synchronized(lock) {
                requests += key
                if (fixture == null) unknownRequests += key
            }
            response(
                request = request,
                code = if (fixture == null) 418 else 200,
                body = fixture ?: """{"detail":"unknown deterministic UI request"}""",
                contentType = if (
                    fixture != null &&
                    request.method == "POST" &&
                    request.url.encodedPath == "/api/chat/stream"
                ) {
                    "text/event-stream; charset=utf-8"
                } else {
                    "application/json; charset=utf-8"
                },
            )
        })
    }

    fun snapshot(): DebugUiAutomationSnapshot = synchronized(lock) {
        DebugUiAutomationSnapshot(requests.toList(), unknownRequests.toList())
    }

    fun assertNoRequestEscapedStub() {
        val snapshot = snapshot()
        check(snapshot.requests.isNotEmpty()) { "deterministic UI mode intercepted no requests" }
        check(snapshot.unknownRequests.isEmpty()) {
            "unknown deterministic UI requests: ${snapshot.unknownRequests.joinToString()}"
        }
    }

    private fun fixture(request: okhttp3.Request): String? {
        if (isExactAnonymousSubjectsRequest(request)) return "[]"
        if (!isExactAuthenticatedRequest(request)) return null
        return when (request.method to request.url.encodedPath) {
        "GET" to "/api/users/me" ->
            """{"id":"7","username":"UI测试","profile":{"age":36,"height_cm":null,"weight_kg":62.0,"display_name":"UI测试"}}"""
        "GET" to "/api/users/settings" -> userSettings(request)
        "GET" to "/api/dashboard/health" -> "{}"
        "GET" to "/api/agent/today" -> "{}"
        "GET" to "/api/health-data/summary" -> "{}"
        "GET" to "/api/health-data/documents" -> """{"items":[],"total":0}"""
        "GET" to "/api/health-data/report-workflows" -> reportHistory(request)
        "GET" to "/api/health-data/report-workflows/501/runtime" -> reportRuntime(request)
        "GET" to "/api/health-data/report-workflows/501/review" -> reportReview(request)
        "GET" to "/api/health-data/indicators" -> """{"indicators":[]}"""
        "GET" to "/api/health-data/indicators/watched" -> """{"items":[]}"""
        "GET" to "/api/health-data/indicators/trend" -> weightTrend()
        "POST" to "/api/health-data/indicators/manual" -> manualIndicator(request)
        "GET" to "/api/chat/conversations" -> "[]"
        "GET" to "/api/health-plans" -> """{"items":[]}"""
        "GET" to "/api/elderly" -> """{"items":[],"total":0}"""
        "GET" to "/api/dietary-records/dashboard" -> dietaryDashboard(request)
        "GET" to "/api/dietary-records/recent" ->
            """{"subject_user_id":7,"items":[]}"""
        "GET" to "/api/dietary-records/daily-summary" ->
            """{"status":"idle","target_date":"2026-08-07","message":"暂无已结束饮食日","summary":null}"""
        "GET" to "/api/health-data/profile-trust" -> healthProfile()
        "GET" to "/api/medications/trust/long-term-summary" ->
            """{"subject_user_id":7,"items":[]}"""
        "GET" to "/api/medications/trust/today" -> medicationToday()
        "GET" to "/api/medications/trust/plans" -> medicationPlans()
        "GET" to "/api/medications/trust/prefill-candidates" ->
            """{"subject_user_id":7,"items":[]}"""
        "GET" to "/api/medications/trust/reactions" ->
            """{"subject_user_id":7,"items":[]}"""
        "GET" to "/api/health-data/medical-assistant/overview" -> medicalAssistant("loaded")
        "POST" to "/api/health-data/medical-assistant/overview/generate" ->
            medicalAssistant("no_information_update")
        "PATCH" to "/api/users/consent" -> """{"allow_ai_chat":true}"""
        "POST" to "/api/chat/stream" -> chatStream(request)
        "POST" to "/api/chat" ->
            """{"answer_markdown":"这是确定性 UI 回答。","summary":"这是确定性 UI 回答。","thread_id":"ui-thread","citations":[]}"""
        else -> null
        }
    }

    private fun userSettings(request: okhttp3.Request): String? {
        if (request.url.query != null || request.body != null) return null
        return """{"intervention_level":"balanced","daily_reminder_limit":3,"glucose_unit":"mmol/L","elderly_mode":false,"elderly_checkin_interval_min":180}"""
    }

    private fun isExactAnonymousSubjectsRequest(request: okhttp3.Request): Boolean =
        request.method == "GET" &&
            request.url.encodedPath == "/api/auth/subjects" &&
            isProductionOrigin(request) &&
            request.url.query == null &&
            request.header("Authorization") == null &&
            request.body == null

    private fun isExactAuthenticatedRequest(request: okhttp3.Request): Boolean =
        isProductionOrigin(request) &&
            request.header("Authorization") == "Bearer ${fakeJwt(subject = "7")}"

    private fun isProductionOrigin(request: okhttp3.Request): Boolean =
        request.url.scheme == "https" &&
            request.url.host == "www.jianjieaitech.com" &&
            request.url.port == 443 &&
            request.url.username.isEmpty() &&
            request.url.password.isEmpty() &&
            request.url.fragment == null

    private fun reportHistory(request: okhttp3.Request): String? {
        val subject = request.url.queryParameter("subject_user_id")
        val from = request.url.queryParameter("date_from")
        val to = request.url.queryParameter("date_to")
        if (subject != "7" || from.isNullOrBlank() || to.isNullOrBlank()) return null
        return """{"items":[{"workflow_id":501,"status":"completed_score_pending","report_type":"exam","title":"年度体检报告","hospital":"市第一人民医院","report_date":"2026-08-01","created_at":"2026-08-01T08:00:00Z"}]}"""
    }

    private fun dietaryDashboard(request: okhttp3.Request): String? {
        val date = request.url.queryParameter("diet_date")?.takeIf(String::isNotBlank) ?: return null
        if (request.url.queryParameter("timezone") != "Asia/Shanghai") return null
        return """{"subject_user_id":7,"selected_date":"$date","is_today":true,"recorded_meal_count":0,"pending_count":0,"streak_days":0,"day_state":"open","records":[],"pending_drafts":[],"selected_day_summary":null,"displayed_summary":null,"displayed_summary_date":"$date","weekly_review":null}"""
    }

    private fun healthProfile(): String =
        """{"subject_user_id":7,"profile_status":"active","overview":{"completeness_percent":0,"resolved_required_weight":0,"total_required_weight":10,"missing_required_fact_keys":[],"pending_update_count":0,"independent_source_count":0,"primary_action":null},"facts":[],"candidates":[],"goals":[],"management_plans":[]}"""

    private fun reportRuntime(request: okhttp3.Request): String? {
        if (request.url.queryParameter("subject_user_id") != "7") return null
        return """{"workflow_id":501,"workflow_version":3,"subject_user_id":7,"state":"completed_score_pending","workflow_status":"completed_score_pending","failure_code":null,"primary_action":{"code":"view_interpretation","enabled":true,"pending_count":0,"target_workflow_id":501}}"""
    }

    private fun reportReview(request: okhttp3.Request): String? {
        if (request.url.queryParameter("subject_user_id") != "7") return null
        return """{"workflow_id":501,"legacy_document_id":null,"subject_user_id":7,"status":"completed_score_pending","version":3,"report_type":"exam","document_fingerprint":null,"recognized_at":"2026-08-01T08:00:00Z","confirmed_at":"2026-08-01T08:03:00Z","completed_at":"2026-08-01T08:04:00Z","confirmation_client_event_id":"ui-confirmation","failure_code":null,"failure_detail":null,"failure_recovery":null,"pending_review_count":0,"auto_accepted_count":2,"admitted_observation_count":2,"requires_report_confirmation":false,"can_confirm":false,"document":null,"candidates":[]}"""
    }

    private fun weightTrend(): String {
        val today = LocalDate.now(ZoneOffset.UTC)
        val old = today.minusMonths(4)
        val first = today.minusDays(75)
        val latest = today.minusDays(1)
        return """{"indicators":[{"name":"体重","unit":"kg","ref_low":null,"ref_high":null,"points":[{"date":"$old","value":80.0,"abnormal":false,"source":"document","measured_at":"${old}T08:00:00Z"},{"date":"$first","value":62.0,"abnormal":false,"source":"manual","measured_at":"${first}T08:00:00Z"},{"date":"$latest","value":70.0,"abnormal":false,"source":"device","measured_at":"${latest}T08:00:00Z","source_metric":"bodyWeight","source_id":"bodyWeight-hc-ui-stable","value_kind":"numeric","source_local_date":"$latest","timezone_offset_minutes":480}]}]}"""
    }

    private fun manualIndicator(request: okhttp3.Request): String {
        val raw = requestBodyUtf8(request)
        val indicator = Regex("\\\"indicator_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(raw)?.groupValues?.get(1) ?: return """{"detail":"invalid manual fixture"}"""
        val value = Regex("\\\"value\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)")
            .find(raw)?.groupValues?.get(1) ?: return """{"detail":"invalid manual fixture"}"""
        val unit = Regex("\\\"unit\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(raw)?.groupValues?.get(1) ?: return """{"detail":"invalid manual fixture"}"""
        val measuredAt = Regex("\\\"measured_at\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(raw)?.groupValues?.get(1) ?: "2026-08-07T08:00:00Z"
        return """{"id":901,"indicator_name":"$indicator","value":$value,"unit":"$unit","measured_at":"$measuredAt","notes":null,"source":"manual"}"""
    }

    private fun medicalAssistant(result: String): String =
        """{"subject_user_id":7,"summary":"已确认资料摘要，仅供就诊沟通参考。","generated_at":"2026-08-07T08:00:00Z","latest_report_uploaded_at":"2026-08-07T08:00:00Z","report_count_last_year":0,"recent_documents":[],"generation_result":"$result"}"""

    private fun chatStream(request: okhttp3.Request): String? {
        val acceptsEventStream = request.headers.values("Accept")
            .flatMap { it.split(',') }
            .any { it.trim().equals("text/event-stream", ignoreCase = true) }
        if (!acceptsEventStream) return null
        val requestType = request.body?.contentType()
        if (requestType?.type != "application" || requestType.subtype != "json") return null

        val payload = runCatching {
            Json.parseToJsonElement(requestBodyUtf8(request)).jsonObject
        }.getOrNull() ?: return null
        val message = (payload["message"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 4_000 }
            ?: return null
        val clientMessageId = (payload["client_message_id"] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 80 }
            ?: return null
        val threadId = when (val value = payload["thread_id"]) {
            null, JsonNull -> null
            is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            else -> return null
        }
        if (threadId != null && threadId != "ui-thread") return null
        check(message.isNotEmpty() && clientMessageId.isNotEmpty())

        return listOf(
            """data: {"type":"route","route":{"version":"2026-07-10","route_id":"ui.health.standard","strategy":"deterministic_fixture","primary_intent":"trend_analysis","depth":"standard","safety_level":"low","subject_type":"self","needs_literature":true,"max_followups":1,"progress_steps":["正在核对确定性测试数据"]}}""",
            """data: {"type":"progress","step":"已核对确定性测试数据"}""",
            """data: {"type":"done","result":{"answer_markdown":"这是确定性 UI 回答。[2]","summary":"这是确定性 UI 回答。[2]","thread_id":"ui-thread","message_id":"ui-message","response_state":"completed","citations":[{"claim_id":1,"literature_id":1,"claim_text":"未被正文引用的测试证据。","evidence_level":"L2","short_ref":"Hidden fixture","confidence":"medium"},{"claim_id":2,"literature_id":2,"claim_text":"正文显式引用的确定性测试证据。","evidence_level":"L1","short_ref":"Visible fixture","population":"确定性人群","study_design":"randomized_controlled_trial","confidence":"high"}]}}""",
            "",
        ).joinToString("\n\n")
    }

    private fun medicationToday(): String =
        """{"subject_user_id":7,"local_date":"2026-08-07","planned_count":1,"taken_count":0,"awaiting_confirmation_count":0,"possibly_missed_count":0,"skipped_count":0,"snoozed_count":0,"adverse_reaction_count":0,"next_task":{"occurrence_key":"dose:v1:11:2026-08-07:20:00","plan_id":11,"plan_version":3,"generic_name":"阿托伐他汀","dose_text":"20mg","scheduled_local_date":"2026-08-07","scheduled_time":"20:00","scheduled_at":"2026-08-07T20:00:00+08:00","status":"upcoming","status_label":"尚未到计划时间","status_assertion":"schedule_derived","occurrence_version":1,"possibly_missed_is_not_confirmation":false,"notification_schedule_status":"client_managed"},"tasks":[{"occurrence_key":"dose:v1:11:2026-08-07:20:00","plan_id":11,"plan_version":3,"generic_name":"阿托伐他汀","dose_text":"20mg","scheduled_local_date":"2026-08-07","scheduled_time":"20:00","scheduled_at":"2026-08-07T20:00:00+08:00","status":"upcoming","status_label":"尚未到计划时间","status_assertion":"schedule_derived","occurrence_version":1,"possibly_missed_is_not_confirmation":false,"notification_schedule_status":"client_managed"}],"missed_assertion_policy":"elapsed_time_never_confirms_missed"}"""

    private fun medicationPlans(): String =
        """{"subject_user_id":7,"items":[{"plan_id":11,"subject_user_id":7,"generic_name":"阿托伐他汀","dose_text":"20mg","frequency":"每日 1 次","schedule_times":["20:00"],"meal_relation":"after_meal","instructions":"饭后服用","course_start":"2026-08-01","course_end":"2026-12-31","source_type":"manual","source_ref":"ui-fixture","status":"active","version":3,"confirmed_at":"2026-08-01T08:00:00+08:00","trust_state":"user_confirmed","reminder_management":"client_managed","reminder_default_enabled":false,"server_notification_scheduled":false,"inventory":{"is_estimate":true,"label":"预计剩余","estimated_remaining":20.0,"inventory_unit":"片","basis":"user_confirmed_taken_events_only"}}]}"""

    private fun requestBodyUtf8(request: okhttp3.Request): String = Buffer().run {
        request.body?.writeTo(this)
        readUtf8()
    }

    private fun response(
        request: okhttp3.Request,
        code: Int,
        body: String,
        contentType: String,
    ): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Deterministic fixture missing")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    private fun fakeJwt(subject: String): String {
        fun segment(raw: String): String = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return "${segment("""{"alg":"none"}""")}.${segment("""{"sub":"$subject"}""")}.debug"
    }
}

data class DebugUiAutomationSnapshot(
    val requests: List<String>,
    val unknownRequests: List<String>,
)
