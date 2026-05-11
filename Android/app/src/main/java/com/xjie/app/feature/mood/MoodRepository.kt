package com.xjie.app.feature.mood

import com.xjie.app.core.model.MoodDay
import com.xjie.app.core.model.MoodGlucoseCorrelation
import com.xjie.app.core.model.MoodLogIn
import com.xjie.app.core.network.api.MoodApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MoodRepository @Inject constructor(
    private val api: MoodApi,
    private val json: Json,
) {
    suspend fun days(d: Int): List<MoodDay> = safeApiCall(json) { api.days(d) }
    suspend fun correlation(d: Int): MoodGlucoseCorrelation? = runCatching {
        safeApiCall(json) { api.correlation(d) }
    }.getOrNull()
    suspend fun checkIn(segment: String, level: Int) = safeApiCall(json) {
        api.create(MoodLogIn(ts = Instant.now().toString(), segment = segment, mood_level = level))
    }
}
