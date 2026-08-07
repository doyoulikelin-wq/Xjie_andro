package com.xjie.app.feature.settings

/** Matches iOS: only the settings root and account/security surface fetch account settings. */
internal object XAgeSettingsLoadPolicy {
    fun requiresAccountSettings(initialSection: String?): Boolean =
        initialSection == null || initialSection == XAgeSupportCompliancePolicy.ACCOUNT_DESTINATION
}
