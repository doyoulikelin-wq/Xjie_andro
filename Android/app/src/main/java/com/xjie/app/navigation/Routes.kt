package com.xjie.app.navigation

/** 全局路由常量。模块完成后陆续补充叶子页面。 */
sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object Login : Route("login")
    data object Main : Route("main")

    // 主 Tab
    data object Home : Route("home")
    data object Glucose : Route("glucose")
    data object Meals : Route("meals")
    data object Health : Route("health")

    // 二级页面
    data object Settings : Route("settings")
    data object Chat : Route("chat")
    data object HealthData : Route("health_data")
    data object PatientHistory : Route("patient_history")
    data object Mood : Route("mood")
    data object Omics : Route("omics")
    data object Admin : Route("admin")
    data object MedicalRecords : Route("medical_records")
    data object ExamReports : Route("exam_reports")

    data class HealthDataFocus(val focus: String) : Route("health_data_focus/$focus") {
        companion object { const val PATTERN = "health_data_focus/{focus}" }
    }

    data class DocumentDetail(val docId: String) : Route("document/$docId") {
        companion object { const val PATTERN = "document/{id}" }
    }

    data class ChatDetail(val conversationId: String) : Route("chat/$conversationId") {
        companion object { const val PATTERN = "chat/{id}" }
    }
}
