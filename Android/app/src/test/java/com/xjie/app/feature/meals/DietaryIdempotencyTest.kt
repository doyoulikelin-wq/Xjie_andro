package com.xjie.app.feature.meals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DietaryIdempotencyTest {
    private data class Payload(val eventId: String, val portion: String, val timezone: String)

    @Test
    fun ambiguousDeliveryReplaysExactEventAndPayloadUntilDefinitiveResolution() {
        var sequence = 0
        val ledger = DietaryIdempotencyLedger { "event-${++sequence}" }
        val first = ledger.prepare("account-a", "confirm:1:2", "POST confirm") { event ->
            Payload(event, "一小碗", "Asia/Shanghai")
        }
        ledger.resolve(first, DietaryDeliveryOutcome.AmbiguousFailure)

        val changedRetry = ledger.prepare("account-a", "confirm:1:2", "POST confirm") { event ->
            Payload(event, "两大碗", "America/Los_Angeles")
        }
        assertEquals(first.eventId, changedRetry.eventId)
        assertEquals(first.payload, changedRetry.payload)
        assertEquals(1, sequence)

        ledger.resolve(changedRetry, DietaryDeliveryOutcome.DefinitiveFailure)
        val afterExplicitRejection = ledger.prepare("account-a", "confirm:1:2", "POST confirm") { event ->
            Payload(event, "两大碗", "Asia/Shanghai")
        }
        assertNotEquals(first.eventId, afterExplicitRejection.eventId)
        assertEquals("两大碗", afterExplicitRejection.payload.portion)
    }

    @Test
    fun accountChangeCannotCarryPendingEventIntoTheNextOwner() {
        var sequence = 0
        val ledger = DietaryIdempotencyLedger { "event-${++sequence}" }
        val accountA = ledger.prepare("account-a", "reuse:3:1", "POST reuse") { it }
        ledger.resolve(accountA, DietaryDeliveryOutcome.AmbiguousFailure)

        val accountB = ledger.prepare("account-b", "reuse:3:1", "POST reuse") { it }
        assertNotEquals(accountA.eventId, accountB.eventId)
        assertEquals(1, ledger.pendingCount())
    }
}
