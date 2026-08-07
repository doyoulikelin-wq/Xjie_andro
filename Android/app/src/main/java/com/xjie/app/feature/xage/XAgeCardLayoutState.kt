package com.xjie.app.feature.xage

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** iOS-parity defaults. A saved customized empty list intentionally remains empty. */
internal object XAgeDefaultCardContract {
    val orderedIds: List<String> = listOf("hrv", "sleep", "glucose", "temp")
}

internal data class XAgeCardLayoutState(
    val isCustomized: Boolean = false,
    val orderedIds: List<String> = emptyList(),
) {
    fun visibleIds(serverIds: List<String>, candidateIds: List<String>): List<String> {
        val servers = serverIds.sanitizedIds()
        val available = (servers + candidateIds).sanitizedIds()
        val availableSet = available.toSet()
        val preferred = if (isCustomized) orderedIds else XAgeDefaultCardContract.orderedIds
        val selected = preferred.filter(availableSet::contains).sanitizedIds()
        // Match iOS's uncustomized merge: stable defaults remain first and real server cards append.
        return if (isCustomized) selected else (selected + servers).sanitizedIds()
    }

    fun withVisibleOrder(visibleIds: List<String>): XAgeCardLayoutState =
        copy(isCustomized = true, orderedIds = visibleIds.sanitizedIds())

    fun adding(
        id: String,
        @Suppress("UNUSED_PARAMETER") isServer: Boolean,
        visibleIds: List<String>,
    ): XAgeCardLayoutState {
        val normalizedId = id.takeIf { it.isValidLayoutId() } ?: return this
        return copy(
            isCustomized = true,
            orderedIds = (visibleIds + normalizedId).sanitizedIds(),
        )
    }

    fun removing(
        id: String,
        @Suppress("UNUSED_PARAMETER") isServer: Boolean,
        visibleIds: List<String>,
    ): XAgeCardLayoutState {
        val normalizedId = id.takeIf { it.isValidLayoutId() } ?: return this
        return copy(
            isCustomized = true,
            orderedIds = visibleIds.filterNot { it == normalizedId }.sanitizedIds(),
        )
    }
}

internal class XAgeCardLayoutStore(private val context: Context) {
    fun load(accountScope: String?): XAgeCardLayoutState {
        val preferences = preferences(accountScope) ?: return XAgeCardLayoutState()
        if (!preferences.contains(KEY_CUSTOMIZED)) return XAgeCardLayoutState()
        return XAgeCardLayoutState(
            isCustomized = preferences.getBoolean(KEY_CUSTOMIZED, false),
            orderedIds = preferences.getString(KEY_ORDERED_IDS, null).decodeIds(),
        )
    }

    fun save(accountScope: String?, state: XAgeCardLayoutState) {
        val preferences = preferences(accountScope) ?: return
        preferences.edit()
            .putBoolean(KEY_CUSTOMIZED, state.isCustomized)
            .putString(KEY_ORDERED_IDS, state.orderedIds.sanitizedIds().joinToString(ID_SEPARATOR))
            .apply()
    }

    private fun preferences(accountScope: String?) = accountScope
        ?.takeIf(String::isNotBlank)
        ?.let { scope ->
            context.applicationContext.getSharedPreferences(
                preferencesName(scope),
                Context.MODE_PRIVATE,
            )
        }

    internal companion object {
        private const val KEY_CUSTOMIZED = "customized"
        private const val KEY_ORDERED_IDS = "ordered_ids"
        private const val ID_SEPARATOR = "\n"

        fun preferencesName(accountScope: String): String {
            require(accountScope.isNotBlank()) { "card layout account scope must not be blank" }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(accountScope.toByteArray(StandardCharsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
            return "xage_card_layout_v2_$digest"
        }
    }
}

private fun String?.decodeIds(): List<String> =
    this?.split('\n').orEmpty().sanitizedIds()

private fun Iterable<String>.sanitizedIds(): List<String> =
    filter(String::isValidLayoutId).distinct()

private fun String.isValidLayoutId(): Boolean = isNotBlank() && '\n' !in this && length <= 256
