package com.xjie.app.feature.xage

import com.xjie.app.feature.settings.XAgeSupportCompliancePolicy

/** Stable, testable navigation contract for the XAge home surface. */
data class XAgeQuickActionSpec(
    val id: String,
    val title: String,
    val destination: String?,
)

data class XAgeHeaderChromePresentation(
    val semanticStatus: String,
    val visibleCaption: String?,
)

/** iOS keeps sync/reorder guidance accessible without adding two visible text rows. */
object XAgeHomeChromePresentationPolicy {
    fun header(caption: String) = XAgeHeaderChromePresentation(
        semanticStatus = caption,
        visibleCaption = null,
    )

    fun visibleQuickActionReorderHint(): String? = null
}

/** The current iOS-parity launcher registry. Hidden legacy entries cannot leak into the strip. */
object XAgeQuickActionRegistry {
    val activeActions: List<XAgeQuickActionSpec> = listOf(
        XAgeQuickActionSpec("meals", "饮食", "meals"),
        XAgeQuickActionSpec("weight", "体重", "weight"),
        XAgeQuickActionSpec("reports", "报告", "reports"),
        XAgeQuickActionSpec("medications", "用药", "medications"),
        XAgeQuickActionSpec("medical", "就医助手", "medical"),
    )

    val activeIds: List<String> = activeActions.map { it.id }

    private val actionsById: Map<String, XAgeQuickActionSpec> = activeActions.associateBy { it.id }

    fun action(id: String): XAgeQuickActionSpec? = actionsById[id]
}

object XAgeInformationArchitecture {
    const val DATA_MANAGER_TITLE = "管理"
    const val PROFILE_DESTINATION = "profile"
    const val DEVICE_DESTINATION = XAgeSupportCompliancePolicy.DEVICE_DESTINATION
    const val FAMILY_DESTINATION = "family"
    const val ACCOUNT_DESTINATION = XAgeSupportCompliancePolicy.ACCOUNT_DESTINATION
    const val SUPPORT_DESTINATION = XAgeSupportCompliancePolicy.SUPPORT_DESTINATION
    const val SUPPORT_HELP_DESTINATION = "support_help"
    const val SUPPORT_VERSION_DESTINATION = "support_version"
    const val SUPPORT_PRIVACY_DESTINATION = "support_privacy"
    const val SUPPORT_PERMISSIONS_DESTINATION = "support_permissions"
    const val SUPPORT_FEEDBACK_DESTINATION = "support_feedback"
    const val WEIGHT_METRIC_ID = "bodyWeight"
    const val PRIVACY_POLICY_UPDATED_AT = XAgeSupportCompliancePolicy.PRIVACY_POLICY_UPDATED_AT
    const val PRIVACY_POLICY_URL = XAgeSupportCompliancePolicy.PRIVACY_POLICY_URL
    const val SUPPORT_EMAIL = XAgeSupportCompliancePolicy.SUPPORT_EMAIL

    val supportDestinationIds: List<String> = XAgeSupportCompliancePolicy.destinationIds

    /** Exactly the five current iOS launchers; management remains the dedicated top-bar action. */
    val quickActions: List<XAgeQuickActionSpec> = XAgeQuickActionRegistry.activeActions

    /** iOS exposes each support utility directly from More rather than through another hub. */
    val moreDestinations: List<String> =
        listOf(
            PROFILE_DESTINATION,
            DEVICE_DESTINATION,
            ACCOUNT_DESTINATION,
            FAMILY_DESTINATION,
            SUPPORT_HELP_DESTINATION,
            SUPPORT_VERSION_DESTINATION,
            SUPPORT_PRIVACY_DESTINATION,
            SUPPORT_PERMISSIONS_DESTINATION,
            SUPPORT_FEEDBACK_DESTINATION,
        )

    fun destinationForMetric(metricId: String): String? =
        if (metricId == WEIGHT_METRIC_ID) "weight" else null

    fun isFeedbackValid(content: String): Boolean =
        XAgeSupportCompliancePolicy.isFeedbackValid(content)

    fun hasFeedbackDraft(content: String, contact: String): Boolean =
        XAgeSupportCompliancePolicy.hasFeedbackDraft(content, contact)
}
