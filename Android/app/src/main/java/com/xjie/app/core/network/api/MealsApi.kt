package com.xjie.app.core.network.api

import com.xjie.app.core.model.MealCreateBody
import com.xjie.app.core.model.MealItem
import com.xjie.app.core.model.MealPhoto
import com.xjie.app.core.model.MealUpdateBody
import com.xjie.app.core.model.MealUploadTicket
import com.xjie.app.core.model.PhotoCompleteBody
import com.xjie.app.core.model.PhotoUploadBody
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface MealsApi {
    @GET("api/meals")
    suspend fun list(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<MealItem>

    @POST("api/meals")
    suspend fun create(@Body body: MealCreateBody): MealItem

    @PATCH("api/meals/{mealId}")
    suspend fun update(@Path("mealId") mealId: String, @Body body: MealUpdateBody): MealItem

    @DELETE("api/meals/{mealId}")
    suspend fun delete(@Path("mealId") mealId: String): retrofit2.Response<Unit>

    @POST("api/meals/photo/upload-url")
    suspend fun requestUploadUrl(@Body body: PhotoUploadBody): MealUploadTicket

    /** 直接 multipart 上传到后端（fallback 路径）。 */
    @Multipart
    @POST("api/meals/photo/upload")
    suspend fun uploadPhotoMultipart(@Part file: MultipartBody.Part): MealPhoto

    @POST("api/meals/photo/complete")
    suspend fun completePhotoUpload(@Body body: PhotoCompleteBody): MealPhoto
}
