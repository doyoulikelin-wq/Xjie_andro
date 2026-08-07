package com.xjie.app.feature.xage

import android.content.Context

internal data class XAgeQuickActionOrderState(
    val orderedIds: List<String> = XAgeQuickActionRegistry.activeIds,
) {
    /** Drops unknown/duplicate ids and appends newly registered actions in registry order. */
    fun resolvedIds(
        availableIds: List<String> = XAgeQuickActionRegistry.activeIds,
    ): List<String> {
        val available = availableIds.filter(String::isValidQuickActionId).distinct()
        val availableSet = available.toSet()
        return (orderedIds.filter(availableSet::contains) + available).distinct()
    }

    fun normalized(): XAgeQuickActionOrderState = copy(orderedIds = resolvedIds())

    fun moving(id: String, targetIndex: Int): XAgeQuickActionOrderState {
        val current = resolvedIds().toMutableList()
        val fromIndex = current.indexOf(id)
        if (fromIndex < 0 || targetIndex !in current.indices || fromIndex == targetIndex) {
            return copy(orderedIds = current)
        }
        val item = current.removeAt(fromIndex)
        current.add(targetIndex, item)
        return copy(orderedIds = current)
    }
}

internal class XAgeQuickActionOrderStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(accountScope: String?): XAgeQuickActionOrderState {
        val key = preferenceKey(accountScope) ?: return XAgeQuickActionOrderState()
        return XAgeQuickActionOrderState(
            orderedIds = preferences.getString(key, null).decodeQuickActionIds(),
        ).normalized()
    }

    fun save(accountScope: String?, state: XAgeQuickActionOrderState) {
        val key = preferenceKey(accountScope) ?: return
        preferences.edit()
            .putString(key, state.resolvedIds().joinToString(ID_SEPARATOR))
            .apply()
    }

    internal companion object {
        private const val PREFERENCES_NAME = "xage_quick_action_order_v1"
        private const val ID_SEPARATOR = "\n"
        private val ACCOUNT_SCOPE_PATTERN = Regex("account-[0-9a-f]{64}")

        fun preferenceKey(accountScope: String?): String? = accountScope
            ?.trim()
            ?.takeIf(ACCOUNT_SCOPE_PATTERN::matches)
            ?.let { "ordered_ids.$it" }
    }
}

private fun String?.decodeQuickActionIds(): List<String> =
    this?.split('\n').orEmpty().filter(String::isValidQuickActionId).distinct()

private fun String.isValidQuickActionId(): Boolean =
    isNotBlank() && '\n' !in this && length <= 128
