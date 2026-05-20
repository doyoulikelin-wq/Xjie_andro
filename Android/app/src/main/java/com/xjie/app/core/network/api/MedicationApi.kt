package com.xjie.app.core.network.api

import com.xjie.app.core.model.Medication
import com.xjie.app.core.model.MedicationBody
import com.xjie.app.core.model.MedicationList
import com.xjie.app.core.model.MedicationRecognizeBody
import com.xjie.app.core.model.MedicationRecognizeResult
import com.xjie.app.core.model.SimpleOk
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface MedicationApi {
    @GET("api/medications")
    suspend fun list(): MedicationList

    @POST("api/medications")
    suspend fun create(@Body body: MedicationBody): Medication

    @PATCH("api/medications/{id}")
    suspend fun update(@Path("id") id: Long, @Body body: MedicationBody): Medication

    @DELETE("api/medications/{id}")
    suspend fun delete(@Path("id") id: Long): SimpleOk

    @POST("api/medications/recognize")
    suspend fun recognize(@Body body: MedicationRecognizeBody): MedicationRecognizeResult
}
