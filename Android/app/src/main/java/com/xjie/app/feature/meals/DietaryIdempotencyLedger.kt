package com.xjie.app.feature.meals

/** Delivery classification for one mutation with a client-generated idempotency event. */
enum class DietaryDeliveryOutcome {
    Success,
    DefinitiveFailure,
    AmbiguousFailure,
}

data class PreparedDietaryOperation<T : Any>(
    val accountScope: String,
    val key: String,
    val operation: String,
    val eventId: String,
    val payload: T,
)

/**
 * Retains the exact first operation/event/payload while delivery is ambiguous. A user edit,
 * time rollover or retry cannot rewrite that snapshot. Success or an explicit non-408 4xx
 * releases it; an account-generation change clears every old-owner snapshot.
 */
class DietaryIdempotencyLedger(
    private val makeEventId: () -> String,
) {
    private data class Entry(
        val accountScope: String,
        val key: String,
        val operation: String,
        val eventId: String,
        val payload: Any,
    )

    private val entries = linkedMapOf<String, Entry>()

    @Synchronized
    fun <T : Any> prepare(
        accountScope: String,
        key: String,
        operation: String,
        buildPayload: (String) -> T,
    ): PreparedDietaryOperation<T> {
        require(accountScope.isNotBlank()) { "dietary operation requires an account scope" }
        require(key.isNotBlank() && operation.isNotBlank()) { "dietary operation identity is required" }
        entries.entries.removeAll { it.value.accountScope != accountScope }

        entries[key]?.let { existing ->
            require(existing.accountScope == accountScope && existing.operation == operation) {
                "pending dietary operation identity changed"
            }
            @Suppress("UNCHECKED_CAST")
            return PreparedDietaryOperation(
                accountScope = existing.accountScope,
                key = existing.key,
                operation = existing.operation,
                eventId = existing.eventId,
                payload = existing.payload as T,
            )
        }

        val eventId = makeEventId().trim()
        require(eventId.isNotEmpty()) { "dietary client event id is required" }
        val payload = buildPayload(eventId)
        val entry = Entry(accountScope, key, operation, eventId, payload)
        entries[key] = entry
        return PreparedDietaryOperation(accountScope, key, operation, eventId, payload)
    }

    @Synchronized
    fun resolve(operation: PreparedDietaryOperation<*>, outcome: DietaryDeliveryOutcome) {
        if (outcome == DietaryDeliveryOutcome.AmbiguousFailure) return
        val existing = entries[operation.key] ?: return
        if (
            existing.accountScope == operation.accountScope &&
            existing.eventId == operation.eventId &&
            existing.operation == operation.operation
        ) {
            entries.remove(operation.key)
        }
    }

    @Synchronized
    fun clearForAccountChange() = entries.clear()

    @Synchronized
    fun pendingCount(): Int = entries.size
}
