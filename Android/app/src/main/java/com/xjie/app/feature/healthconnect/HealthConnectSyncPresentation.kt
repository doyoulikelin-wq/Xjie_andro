package com.xjie.app.feature.healthconnect

import android.content.Context
import com.xjie.app.core.auth.AuthManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** The two Health Connect surfaces intentionally expose different information density. */
enum class HealthConnectCardSurface {
    CompactHome,
    FullManager,
}

data class HealthConnectCardPresentation(
    val isVisible: Boolean,
    val title: String,
    val subtitle: String,
    val buttonTitle: String,
    val showsDetailedStatus: Boolean,
    val detailBadges: List<String>,
)

/**
 * Keeps the home authorization affordance compact while the manager remains the sole full-status
 * surface. A successful sync hides only the home affordance; it never removes the manager entry.
 */
object HealthConnectCardPresentationPolicy {
    fun presentation(
        surface: HealthConnectCardSurface,
        state: HealthConnectSyncUiState,
    ): HealthConnectCardPresentation {
        val working = state.phase == HealthConnectSyncPhase.Syncing
        val availability = when (state.availability) {
            HealthConnectAvailability.Available -> "可用"
            HealthConnectAvailability.ProviderUpdateRequired -> "需要更新"
            HealthConnectAvailability.Unavailable -> "不支持"
        }
        val buttonTitle = when {
            working -> "同步中"
            surface == HealthConnectCardSurface.CompactHome -> "授权"
            state.hasSuccessfulSync -> "同步"
            state.phase == HealthConnectSyncPhase.PermissionRequired -> "授权"
            else -> "连接"
        }
        return when (surface) {
            HealthConnectCardSurface.CompactHome -> HealthConnectCardPresentation(
                isVisible = !state.hasSuccessfulSync,
                title = "Health Connect",
                subtitle = "授权后可以更好地评估当前的身体指标",
                buttonTitle = buttonTitle,
                showsDetailedStatus = false,
                detailBadges = emptyList(),
            )
            HealthConnectCardSurface.FullManager -> HealthConnectCardPresentation(
                isVisible = true,
                title = "Health Connect 同步",
                subtitle = state.message,
                buttonTitle = buttonTitle,
                showsDetailedStatus = true,
                detailBadges = listOf(
                    availability,
                    "只读授权",
                    "${state.syncedCount} 项已同步",
                ),
            )
        }
    }
}

internal data class HealthConnectSuccessfulSyncReceipt(
    val ownerBinding: String,
    val syncedAtEpochMillis: Long,
)

internal interface HealthConnectSuccessfulSyncBackend {
    fun read(): HealthConnectSuccessfulSyncReceipt?
    fun write(receipt: HealthConnectSuccessfulSyncReceipt): Boolean
}

/**
 * Stores only a one-way owner binding and timestamp. The binding includes account, selected subject,
 * and monotonic auth generation, so process recreation restores the same session while A -> B -> A
 * cannot reuse A's earlier success.
 */
@Singleton
class HealthConnectSuccessfulSyncStore private constructor(
    private val backend: HealthConnectSuccessfulSyncBackend,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesHealthConnectSuccessfulSyncBackend(
            context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun restore(owner: AuthManager.AccountScopeSnapshot): Long? {
        val receipt = backend.read() ?: return null
        if (receipt.ownerBinding != ownerBinding(owner)) return null
        return receipt.syncedAtEpochMillis.takeIf { it > 0L }
    }

    fun record(owner: AuthManager.AccountScopeSnapshot, syncedAtEpochMillis: Long): Boolean {
        require(syncedAtEpochMillis > 0L) { "successful Health Connect sync time must be positive" }
        return backend.write(
            HealthConnectSuccessfulSyncReceipt(
                ownerBinding = ownerBinding(owner),
                syncedAtEpochMillis = syncedAtEpochMillis,
            ),
        )
    }

    private fun ownerBinding(owner: AuthManager.AccountScopeSnapshot): String {
        val canonical = listOf(
            owner.accountScope,
            owner.subjectId,
            owner.generation.toString(),
        ).joinToString(separator = "\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    internal companion object {
        private const val PREFERENCES_NAME = "health_connect_successful_sync_v1"

        fun forTesting(backend: HealthConnectSuccessfulSyncBackend) =
            HealthConnectSuccessfulSyncStore(backend)
    }
}

private class SharedPreferencesHealthConnectSuccessfulSyncBackend(
    private val preferences: android.content.SharedPreferences,
) : HealthConnectSuccessfulSyncBackend {
    override fun read(): HealthConnectSuccessfulSyncReceipt? {
        val binding = preferences.getString(KEY_OWNER_BINDING, null) ?: return null
        if (!preferences.contains(KEY_SYNCED_AT)) return null
        return HealthConnectSuccessfulSyncReceipt(
            ownerBinding = binding,
            syncedAtEpochMillis = preferences.getLong(KEY_SYNCED_AT, 0L),
        )
    }

    override fun write(receipt: HealthConnectSuccessfulSyncReceipt): Boolean =
        preferences.edit()
            .putString(KEY_OWNER_BINDING, receipt.ownerBinding)
            .putLong(KEY_SYNCED_AT, receipt.syncedAtEpochMillis)
            .commit()

    private companion object {
        const val KEY_OWNER_BINDING = "owner_binding"
        const val KEY_SYNCED_AT = "synced_at_epoch_millis"
    }
}
