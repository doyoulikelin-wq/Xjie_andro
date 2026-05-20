package com.xjie.app.feature.medication

import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import com.xjie.app.core.model.MedicationRecognizeBody
import com.xjie.app.core.model.MedicationRecognizeResult
import com.xjie.app.core.model.SimpleOk
import com.xjie.app.core.network.api.MedicationApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepository @Inject constructor(
    private val api: MedicationApi,
    private val json: Json,
) {
    suspend fun list(): List<Medication> = safeApiCall(json) { api.list() }.items
    suspend fun create(body: MedicationBody): Medication = safeApiCall(json) { api.create(body) }
    suspend fun update(id: Long, body: MedicationBody): Medication =
        safeApiCall(json) { api.update(id, body) }
    suspend fun delete(id: Long): SimpleOk = safeApiCall(json) { api.delete(id) }
    suspend fun recognize(rawText: String): MedicationRecognizeResult =
        safeApiCall(json) { api.recognize(MedicationRecognizeBody(rawText)) }
}
