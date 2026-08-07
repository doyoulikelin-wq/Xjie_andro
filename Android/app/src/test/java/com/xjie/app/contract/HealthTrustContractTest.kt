package com.xjie.app.contract

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthTrustContractTest {
    private val contractBytes: ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/health_trust_contract.json"),
    ) { "health_trust_contract.json must be packaged as a JVM test resource" }.use { it.readBytes() }

    private val contract = Json.parseToJsonElement(contractBytes.decodeToString()).jsonObject

    @Test
    fun healthTrustContractRejectsUnconfirmedAdmissionAndXAgeEnablement() {
        assertEquals(
            "05aee2f3c0a8404146b06378e4833ad70679f1640eb35d23596a7eee1c809933",
            contractBytes.sha256(),
        )
        assertEquals("HEALTH-TRUST-001", contract.string("contract_id"))
        assertEquals("server", contract.string("authority"))
        assertEquals(
            listOf(
                "draft",
                "uploading",
                "recognizing",
                "awaiting_confirmation",
                "committing",
                "completed",
                "completed_score_pending",
                "failed",
            ),
            contract.strings("report_workflow_states"),
        )

        val invariants = contract.getValue("invariants").jsonObject
        assertTrue(invariants.boolean("report_level_user_confirmation_is_required_before_admission"))
        assertTrue(invariants.boolean("unadmitted_candidates_are_excluded_from_trends"))
        assertTrue(invariants.boolean("unadmitted_candidates_are_excluded_from_profile"))
        assertTrue(invariants.boolean("unadmitted_candidates_are_excluded_from_ai"))
        assertTrue(invariants.boolean("unadmitted_candidates_are_excluded_from_scores"))
        assertTrue(invariants.boolean("safety_facts_never_auto_confirm"))
        assertTrue(invariants.boolean("provenance_chain_is_complete"))
        assertTrue(invariants.boolean("xage_consumption_is_disabled_until_separately_validated"))

        assertTrue(
            "report_level_confirmation->admitted_observation" in
                contract.strings("required_provenance_edges"),
        )
        assertFalse(
            contract.getValue("legacy_migration")
                .jsonObject.getValue("legacy_unverified_is_admitted").jsonPrimitive.boolean,
        )
        assertFalse(
            contract.getValue("xage_consumption")
                .jsonObject.getValue("enabled").jsonPrimitive.boolean,
        )
    }

    @Test
    fun dailyEstimateNeverEnablesTrustedScoreOrXAge() {
        val invariants = contract.getValue("invariants").jsonObject
        val dailyEstimate = contract.getValue("daily_estimate").jsonObject

        assertTrue(invariants.boolean("daily_estimates_never_enable_trusted_scores_or_xage"))
        assertEquals("xage.daily.estimate.v1", dailyEstimate.string("algorithm_version"))
        assertEquals("local", dailyEstimate.string("authority"))
        assertEquals("daily_reference", dailyEstimate.string("display_channel"))
        assertFalse(dailyEstimate.boolean("trusted_score_channel_enabled"))
        assertFalse(dailyEstimate.boolean("xage_consumption_enabled"))
        assertTrue(dailyEstimate.boolean("server_snapshot_version_must_be_null"))
        assertEquals(
            listOf("document", "manual", "device", "cgm", "apple_health"),
            dailyEstimate.strings("admitted_server_sources"),
        )
    }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.string(key: String): String =
        getValue(key).jsonPrimitive.content

    private fun Map<String, kotlinx.serialization.json.JsonElement>.strings(key: String): List<String> =
        getValue(key).jsonArray.map { it.jsonPrimitive.content }

    private fun Map<String, kotlinx.serialization.json.JsonElement>.boolean(key: String): Boolean =
        getValue(key).jsonPrimitive.boolean

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
