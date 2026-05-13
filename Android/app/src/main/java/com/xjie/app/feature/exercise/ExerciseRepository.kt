package com.xjie.app.feature.exercise

import com.xjie.app.core.model.ExerciseBody
import com.xjie.app.core.model.ExerciseListResponse
import com.xjie.app.core.model.SimpleOk
import com.xjie.app.core.network.api.ExerciseApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val api: ExerciseApi,
    private val json: Json,
) {
    suspend fun listToday(): ExerciseListResponse = safeApiCall(json) { api.list(date = null) }
    suspend fun create(body: ExerciseBody) = safeApiCall(json) { api.create(body) }
    suspend fun delete(id: Long): SimpleOk = safeApiCall(json) { api.delete(id) }
}
