package com.xjie.app.feature.medication

import com.xjie.app.core.model.MedicationTodaySummary
import com.xjie.app.core.model.MedicationTodayTask
import com.xjie.app.core.model.TrustedMedicationPlan

/** The dashboard has one finite, server-backed hero state. It never invents a dose locally. */
internal sealed interface MedicationDashboardHeroState {
    data object Loading : MedicationDashboardHeroState
    data object NoMedication : MedicationDashboardHeroState
    data class NextDose(val task: MedicationTodayTask) : MedicationDashboardHeroState
    data class AllHandled(val message: String) : MedicationDashboardHeroState
}

internal object MedicationDashboardPresentation {
    fun hero(
        today: MedicationTodaySummary?,
        plans: List<TrustedMedicationPlan>,
        loading: Boolean,
    ): MedicationDashboardHeroState {
        if (loading && today == null) return MedicationDashboardHeroState.Loading
        if (plans.none { it.status != "retracted" }) {
            return MedicationDashboardHeroState.NoMedication
        }
        return today?.next_task?.let(MedicationDashboardHeroState::NextDose)
            ?: MedicationDashboardHeroState.AllHandled(
                today?.empty_state ?: "今天的用药已全部处理",
            )
    }
}

enum class MedicationNotificationPermissionState {
    Unknown,
    NotDetermined,
    Allowed,
    Denied,
    Unavailable,
}

enum class MedicationExactAlarmAccessState {
    Allowed,
    Required,
    Unavailable,
}

internal enum class MedicationReminderTone { Active, Neutral, Warning }

internal data class MedicationDashboardReminderState(
    val title: String,
    val compactTitle: String,
    val detail: String,
    val tone: MedicationReminderTone,
)

/**
 * A reminder is presented as scheduled only when every real Android boundary is currently true.
 */
internal object MedicationReminderPresentation {
    fun resolve(
        task: MedicationTodayTask,
        plans: List<TrustedMedicationPlan>,
        settings: TrustedMedicationReminderSettings?,
        notificationPermission: MedicationNotificationPermissionState,
        exactAlarmAccess: MedicationExactAlarmAccessState,
        scheduledCount: Int,
        owner: MedicationReminderOwner,
        timezoneId: String,
    ): MedicationDashboardReminderState {
        val plan = plans.firstOrNull { it.plan_id == task.plan_id }
            ?: return MedicationDashboardReminderState(
                title = "提醒信息暂不可用",
                compactTitle = "提醒不可用",
                detail = "当前剂次没有匹配到已确认计划",
                tone = MedicationReminderTone.Warning,
            )

        when (notificationPermission) {
            MedicationNotificationPermissionState.Denied -> return MedicationDashboardReminderState(
                title = "通知权限已关闭",
                compactTitle = "权限已关闭",
                detail = "打开提醒设置可前往系统设置恢复",
                tone = MedicationReminderTone.Warning,
            )
            MedicationNotificationPermissionState.Unavailable -> return MedicationDashboardReminderState(
                title = "当前环境不能使用系统通知",
                compactTitle = "通知不可用",
                detail = "提醒没有被冒充为已安排",
                tone = MedicationReminderTone.Warning,
            )
            MedicationNotificationPermissionState.Unknown -> return MedicationDashboardReminderState(
                title = "正在检查提醒状态",
                compactTitle = "检查提醒",
                detail = "稍后会按当前账号与计划版本核对",
                tone = MedicationReminderTone.Neutral,
            )
            MedicationNotificationPermissionState.NotDetermined,
            MedicationNotificationPermissionState.Allowed,
            -> Unit
        }

        val current = settings?.let {
            TrustedMedicationReminderPolicy.isCurrentForPlan(
                settings = it,
                plan = plan,
                owner = owner,
                timezoneId = timezoneId,
            )
        } == true
        if (settings?.enabled == true && !current) {
            return MedicationDashboardReminderState(
                title = "计划、账号或时区已更新，请重新确认提醒",
                compactTitle = "需重设提醒",
                detail = "旧版本通知已停止",
                tone = MedicationReminderTone.Warning,
            )
        }
        if (settings?.enabled == true && exactAlarmAccess != MedicationExactAlarmAccessState.Allowed) {
            return MedicationDashboardReminderState(
                title = "精确闹钟权限未开启",
                compactTitle = "闹钟未授权",
                detail = "保存开启时才会请求系统的精确闹钟权限",
                tone = MedicationReminderTone.Warning,
            )
        }
        if (settings?.enabled == true &&
            current &&
            notificationPermission == MedicationNotificationPermissionState.Allowed &&
            exactAlarmAccess == MedicationExactAlarmAccessState.Allowed &&
            scheduledCount > 0
        ) {
            return MedicationDashboardReminderState(
                title = "下一次用药提醒已安排",
                compactTitle = "提醒已安排",
                detail = "已实际安排 $scheduledCount 个本机闹钟；仍需你在应用内确认",
                tone = MedicationReminderTone.Active,
            )
        }
        if (settings?.enabled == true) {
            return MedicationDashboardReminderState(
                title = "提醒尚未实际安排",
                compactTitle = "提醒未安排",
                detail = "没有成功排期的本机闹钟，不能显示为已安排",
                tone = MedicationReminderTone.Warning,
            )
        }
        return MedicationDashboardReminderState(
            title = "下一次用药提醒未开启",
            compactTitle = "设置提醒",
            detail = if (notificationPermission == MedicationNotificationPermissionState.NotDetermined) {
                "点此设置；保存开启时才会请求通知和精确闹钟权限"
            } else {
                "点此设置提醒时间、声音与锁屏隐私"
            },
            tone = MedicationReminderTone.Neutral,
        )
    }
}

internal enum class MedicationReminderSchedulingState {
    Disabled,
    NotificationPermissionRequired,
    ExactAlarmPermissionRequired,
    NoUpcomingTrigger,
    ScheduleFailed,
    Scheduled,
}

internal object MedicationReminderSchedulingPolicy {
    fun resolve(
        requestedEnabled: Boolean,
        notificationPermission: MedicationNotificationPermissionState,
        exactAlarmAccess: MedicationExactAlarmAccessState,
        upcomingTriggerCount: Int,
        successfulScheduleCount: Int,
        persistenceSucceeded: Boolean,
    ): MedicationReminderSchedulingState {
        if (!requestedEnabled) {
            return if (persistenceSucceeded) {
                MedicationReminderSchedulingState.Disabled
            } else {
                MedicationReminderSchedulingState.ScheduleFailed
            }
        }
        if (notificationPermission != MedicationNotificationPermissionState.Allowed) {
            return MedicationReminderSchedulingState.NotificationPermissionRequired
        }
        if (exactAlarmAccess != MedicationExactAlarmAccessState.Allowed) {
            return MedicationReminderSchedulingState.ExactAlarmPermissionRequired
        }
        if (upcomingTriggerCount == 0) {
            return MedicationReminderSchedulingState.NoUpcomingTrigger
        }
        if (!persistenceSucceeded || successfulScheduleCount != upcomingTriggerCount) {
            return MedicationReminderSchedulingState.ScheduleFailed
        }
        return MedicationReminderSchedulingState.Scheduled
    }
}

internal data class MedicationNotificationPresentation(
    val title: String,
    val body: String,
    val publicTitle: String,
    val publicBody: String,
    val exposePrivateContentOnLockScreen: Boolean,
)

internal object MedicationNotificationPresentationPolicy {
    fun resolve(
        genericName: String,
        doseText: String?,
        instructions: String?,
        showMedicationNameOnLockScreen: Boolean,
    ): MedicationNotificationPresentation {
        val genericTitle = "用药提醒"
        val genericBody = "请打开小捷核对已确认的用药计划。"
        if (!showMedicationNameOnLockScreen) {
            return MedicationNotificationPresentation(
                title = genericTitle,
                body = genericBody,
                publicTitle = genericTitle,
                publicBody = "请打开小捷查看提醒",
                exposePrivateContentOnLockScreen = false,
            )
        }
        val detail = listOfNotNull(
            doseText?.takeIf(String::isNotBlank)?.let { "剂量：$it" },
            instructions?.takeIf(String::isNotBlank),
        ).joinToString("\n").ifBlank { "请打开小捷确认本次服药任务。" }
        return MedicationNotificationPresentation(
            title = "该服药了：$genericName",
            body = detail,
            publicTitle = "该服药了：$genericName",
            publicBody = detail,
            exposePrivateContentOnLockScreen = true,
        )
    }
}
