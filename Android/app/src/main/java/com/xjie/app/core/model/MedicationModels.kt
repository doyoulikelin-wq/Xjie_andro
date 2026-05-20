package com.xjie.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    val id: Long,
    val name: String,
    val dosage: String? = null,
    val frequency: String? = null,
    val instructions: String? = null,
    val schedule_times: List<String> = emptyList(),
    val course_start: String? = null,
    val course_end: String? = null,
    val photo_url: String? = null,
    val enabled: Boolean = true,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class MedicationList(val items: List<Medication> = emptyList())

@Serializable
data class MedicationBody(
    val name: String,
    val dosage: String? = null,
    val frequency: String? = null,
    val instructions: String? = null,
    val schedule_times: List<String> = emptyList(),
    val course_start: String? = null,
    val course_end: String? = null,
    val photo_url: String? = null,
    val enabled: Boolean = true,
)

@Serializable
data class MedicationRecognizeBody(val raw_text: String)

@Serializable
data class MedicationRecognizeResult(
    val name: String? = null,
    val dosage: String? = null,
    val frequency: String? = null,
    val instructions: String? = null,
    val schedule_times: List<String> = emptyList(),
)
