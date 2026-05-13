package com.xjie.app.feature.meals

import android.content.Context
import android.net.Uri
import com.xjie.app.core.model.MealCreateBody
import com.xjie.app.core.model.MealItem
import com.xjie.app.core.model.MealPhoto
import com.xjie.app.core.model.MealUploadTicket
import com.xjie.app.core.model.PhotoCompleteBody
import com.xjie.app.core.model.PhotoUploadBody
import com.xjie.app.core.network.UploadClient
import com.xjie.app.core.network.api.DashboardApi
import com.xjie.app.core.network.api.MealsApi
import com.xjie.app.core.network.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealsRepository @Inject constructor(
    private val api: MealsApi,
    private val dashboardApi: DashboardApi,
    private val json: Json,
    @ApplicationContext private val context: Context,
    @UploadClient private val okHttpClient: okhttp3.OkHttpClient,
) {
    suspend fun list(): List<MealItem> {
        val now = Instant.now()
        val from = now.minusSeconds(30L * 86400)
        val fmt = DateTimeFormatter.ISO_INSTANT
        return safeApiCall(json) { api.list(from = fmt.format(from), to = fmt.format(now)) }
    }

    suspend fun photos(): List<MealPhoto> = safeApiCall(json) { dashboardApi.meals() }

    suspend fun createManual(kcal: Int) {
        val body = MealCreateBody(
            meal_ts = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            meal_ts_source = "user_confirmed",
            kcal = kcal, tags = emptyList(), notes = "",
        )
        safeApiCall(json) { api.create(body) }
    }

    suspend fun updateMeal(mealId: String, kcal: Int?, notes: String?) {
        safeApiCall(json) {
            api.update(mealId, com.xjie.app.core.model.MealUpdateBody(kcal = kcal, notes = notes))
        }
    }

    suspend fun deleteMeal(mealId: String) {
        val resp = api.delete(mealId)
        if (!resp.isSuccessful) throw IllegalStateException("\u5220\u9664\u5931\u8d25: ${resp.code()}")
    }

    suspend fun uploadPhoto(uri: Uri, filename: String = "meal.jpg"): MealPhoto {
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("无法读取拍照文件（请重试）")
        } catch (e: SecurityException) {
            throw IllegalStateException("访问拍照文件被拒绝：${e.message}")
        } catch (e: Exception) {
            throw IllegalStateException("读取拍照失败：${e.message}")
        }
        if (bytes.isEmpty()) throw IllegalStateException("拍照文件为空，请重拍。")
        android.util.Log.i("MealsRepository", "uploadPhoto bytes=${bytes.size} filename=$filename")
        return uploadPhotoBytes(bytes, filename)
    }

    suspend fun uploadPhotoBytes(bytes: ByteArray, filename: String = "meal.jpg"): MealPhoto {
        val ticket: MealUploadTicket = safeApiCall(json) {
            api.requestUploadUrl(PhotoUploadBody(filename, "image/jpeg"))
        }
        android.util.Log.i("MealsRepository",
            "ticket upload_url=${ticket.upload_url} object_key=${ticket.object_key}")
        val uploadUrl = ticket.upload_url
        if (!uploadUrl.isNullOrBlank()) {
            uploadToSignedUrl(uploadUrl, bytes, filename)
        } else {
            val part = MultipartBody.Part.createFormData(
                "file", filename,
                bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
            safeApiCall(json) { api.uploadPhotoMultipart(part) }
        }
        val key = ticket.object_key ?: throw IllegalStateException("上传凭证缺少 object_key")
        return safeApiCall(json) { api.completePhotoUpload(PhotoCompleteBody(key)) }
    }

    private fun uploadToSignedUrl(url: String, bytes: ByteArray, filename: String) {
        val fullUrl = if (url.startsWith("http")) url else {
            // Backend returns paths like "/api/meals/photo/mock-upload/..." which must be
            // appended to base host. Base URL already ends with /api, so concatenating gives
            // /api/api/... which matches the production nginx + FastAPI mount.
            val base = com.xjie.app.BuildConfig.API_BASE_URL.trimEnd('/')
            base + if (url.startsWith("/")) url else "/$url"
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", filename,
                bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()),
            ).build()
        val req = Request.Builder().url(fullUrl).put(multipart).build()
        android.util.Log.i("MealsRepository", "PUT $fullUrl bytes=${bytes.size}")
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val body = runCatching { resp.body?.string()?.take(300) }.getOrNull()
                android.util.Log.e("MealsRepository", "PUT failed: ${resp.code} body=$body url=$fullUrl")
                throw IllegalStateException("上传失败 HTTP ${resp.code} - ${body ?: ""}")
            }
        }
    }
}
