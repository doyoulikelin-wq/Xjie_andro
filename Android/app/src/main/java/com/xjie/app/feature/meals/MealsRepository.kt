package com.xjie.app.feature.meals

import android.content.Context
import android.net.Uri
import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.DietaryDailySummaryStatus
import com.xjie.app.core.model.DietaryDashboardResponse
import com.xjie.app.core.model.DietaryDayCompleteBody
import com.xjie.app.core.model.DietaryDayCompletionResponse
import com.xjie.app.core.model.DietaryDraftConfirmBody
import com.xjie.app.core.model.DietaryDraftCreateBody
import com.xjie.app.core.model.DietaryDraftRetryBody
import com.xjie.app.core.model.DietaryMealDraft
import com.xjie.app.core.model.DietaryMealRecord
import com.xjie.app.core.model.DietaryMutationBody
import com.xjie.app.core.model.DietaryRecentResponse
import com.xjie.app.core.model.DietaryRecordReuseBody
import com.xjie.app.core.model.DietaryRecordUpdateBody
import com.xjie.app.core.model.MealItem
import com.xjie.app.core.network.api.MealsApi
import com.xjie.app.core.network.safeApiCall
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class DietaryPhotoPayload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
)

interface MealsDataSource {
    suspend fun dashboard(
        owner: AuthManager.AccountScopeSnapshot,
        dietDate: String,
        subjectUserId: Long? = null,
    ): DietaryDashboardResponse

    suspend fun dailySummary(owner: AuthManager.AccountScopeSnapshot): DietaryDailySummaryStatus
    suspend fun recent(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long? = null,
        limit: Int = 12,
    ): DietaryRecentResponse
    suspend fun createDraft(owner: AuthManager.AccountScopeSnapshot, body: DietaryDraftCreateBody): DietaryMealDraft
    suspend fun readPhoto(uri: Uri): DietaryPhotoPayload
    suspend fun createPhotoDraft(
        owner: AuthManager.AccountScopeSnapshot,
        payload: DietaryPhotoPayload,
        eventId: String,
        dietDate: String,
        mealType: String,
        eatenAt: String,
        source: String,
        subjectUserId: Long?,
    ): DietaryMealDraft

    suspend fun retryRecognition(owner: AuthManager.AccountScopeSnapshot, draftId: Long, body: DietaryDraftRetryBody): DietaryMealDraft
    suspend fun confirmDraft(owner: AuthManager.AccountScopeSnapshot, draftId: Long, body: DietaryDraftConfirmBody): DietaryMealRecord
    suspend fun updateRecord(owner: AuthManager.AccountScopeSnapshot, recordId: Long, body: DietaryRecordUpdateBody): DietaryMealRecord
    suspend fun deleteRecord(owner: AuthManager.AccountScopeSnapshot, recordId: Long, body: DietaryMutationBody): DietaryMealRecord
    suspend fun reuseRecord(owner: AuthManager.AccountScopeSnapshot, recordId: Long, body: DietaryRecordReuseBody): DietaryMealDraft
    suspend fun completeDay(owner: AuthManager.AccountScopeSnapshot, dietDate: String, body: DietaryDayCompleteBody): DietaryDayCompletionResponse

    /** Read-only bridge for old consumers; trusted writes never use this route. */
    suspend fun legacyMeals(): List<MealItem>
}

@Singleton
class MealsRepository @Inject constructor(
    private val api: MealsApi,
    private val json: Json,
    @ApplicationContext private val context: Context,
) : MealsDataSource {
    override suspend fun dashboard(
        owner: AuthManager.AccountScopeSnapshot,
        dietDate: String,
        subjectUserId: Long?,
    ): DietaryDashboardResponse = safeApiCall(json) {
        api.dietaryDashboard(
            owner = owner,
            dietDate = dietDate,
            timezone = "Asia/Shanghai",
            subjectUserId = subjectUserId,
        )
    }

    override suspend fun dailySummary(owner: AuthManager.AccountScopeSnapshot): DietaryDailySummaryStatus =
        safeApiCall(json) { api.dietaryDailySummary(owner) }

    override suspend fun recent(
        owner: AuthManager.AccountScopeSnapshot,
        subjectUserId: Long?,
        limit: Int,
    ): DietaryRecentResponse =
        safeApiCall(json) {
            api.recentDietaryRecords(owner, limit.coerceIn(1, 30), subjectUserId)
        }

    override suspend fun createDraft(
        owner: AuthManager.AccountScopeSnapshot,
        body: DietaryDraftCreateBody,
    ): DietaryMealDraft = safeApiCall(json) { api.createDietaryDraft(owner, body) }

    override suspend fun readPhoto(uri: Uri): DietaryPhotoPayload = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: "image/jpeg"
        require(mime in ALLOWED_IMAGE_TYPES) { "仅支持 JPEG、PNG、HEIC、HEIF 或 WebP 餐食图片" }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_IMAGE_BYTES) { "餐食图片不能超过 10 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取所选餐食图片")
        require(bytes.isNotEmpty()) { "餐食图片为空，请重新选择" }
        require(bytes.size <= MAX_IMAGE_BYTES) { "餐食图片不能超过 10 MB" }
        DietaryPhotoPayload(
            bytes = bytes.copyOf(),
            fileName = when (mime) {
                "image/png" -> "meal.png"
                "image/heic" -> "meal.heic"
                "image/heif" -> "meal.heif"
                "image/webp" -> "meal.webp"
                else -> "meal.jpg"
            },
            mimeType = mime,
        )
    }

    override suspend fun createPhotoDraft(
        owner: AuthManager.AccountScopeSnapshot,
        payload: DietaryPhotoPayload,
        eventId: String,
        dietDate: String,
        mealType: String,
        eatenAt: String,
        source: String,
        subjectUserId: Long?,
    ): DietaryMealDraft {
        val file = MultipartBody.Part.createFormData(
            "file",
            payload.fileName,
            payload.bytes.toRequestBody(payload.mimeType.toMediaType()),
        )
        fun text(value: String) = value.toRequestBody("text/plain".toMediaType())
        return safeApiCall(json) {
            api.createDietaryPhotoDraft(
                owner = owner,
                file = file,
                clientEventId = text(eventId),
                dietDate = text(dietDate),
                mealType = text(mealType),
                eatenAt = text(eatenAt),
                source = text(source),
                timezone = text("Asia/Shanghai"),
                subjectUserId = subjectUserId?.let { text(it.toString()) },
            )
        }
    }

    override suspend fun retryRecognition(
        owner: AuthManager.AccountScopeSnapshot,
        draftId: Long,
        body: DietaryDraftRetryBody,
    ): DietaryMealDraft = safeApiCall(json) { api.retryDietaryRecognition(owner, draftId, body) }

    override suspend fun confirmDraft(
        owner: AuthManager.AccountScopeSnapshot,
        draftId: Long,
        body: DietaryDraftConfirmBody,
    ): DietaryMealRecord = safeApiCall(json) { api.confirmDietaryDraft(owner, draftId, body) }

    override suspend fun updateRecord(
        owner: AuthManager.AccountScopeSnapshot,
        recordId: Long,
        body: DietaryRecordUpdateBody,
    ): DietaryMealRecord = safeApiCall(json) { api.updateDietaryRecord(owner, recordId, body) }

    override suspend fun deleteRecord(
        owner: AuthManager.AccountScopeSnapshot,
        recordId: Long,
        body: DietaryMutationBody,
    ): DietaryMealRecord = safeApiCall(json) { api.deleteDietaryRecord(owner, recordId, body) }

    override suspend fun reuseRecord(
        owner: AuthManager.AccountScopeSnapshot,
        recordId: Long,
        body: DietaryRecordReuseBody,
    ): DietaryMealDraft = safeApiCall(json) { api.reuseDietaryRecord(owner, recordId, body) }

    override suspend fun completeDay(
        owner: AuthManager.AccountScopeSnapshot,
        dietDate: String,
        body: DietaryDayCompleteBody,
    ): DietaryDayCompletionResponse = safeApiCall(json) { api.completeDietaryDay(owner, dietDate, body) }

    override suspend fun legacyMeals(): List<MealItem> {
        val now = Instant.now()
        val from = now.minusSeconds(30L * 86_400L)
        val formatter = DateTimeFormatter.ISO_INSTANT
        return safeApiCall(json) {
            api.list(from = formatter.format(from), to = formatter.format(now))
        }
    }

    // Temporary source compatibility while the UI is migrated in the same change. These methods
    // deliberately fail closed for legacy writes so an intermediate caller cannot admit an
    // unconfirmed recognition into /api/meals.
    @Deprecated("Use dashboard")
    suspend fun list(): List<MealItem> = legacyMeals()

    @Deprecated("Legacy photo projections are not trusted dietary records")
    suspend fun photos(): List<com.xjie.app.core.model.MealPhoto> = emptyList()

    @Deprecated("Use createDraft then confirmDraft")
    suspend fun createManual(kcal: Int): Nothing =
        error("旧热量直写已停用，请先创建并确认膳食草稿（$kcal kcal 未写入）")

    @Deprecated("Use versioned updateRecord")
    suspend fun updateMeal(mealId: String, kcal: Int?, notes: String?): Nothing =
        error("旧膳食修改已停用，请刷新可信记录后修改（$mealId）")

    @Deprecated("Use versioned deleteRecord")
    suspend fun deleteMeal(mealId: String): Nothing =
        error("旧膳食删除已停用，请刷新可信记录后删除（$mealId）")

    @Deprecated("Use createPhotoDraft")
    suspend fun uploadPhoto(uri: Uri): Nothing =
        error("旧照片直写已停用，请通过待确认草稿上传（$uri）")

    private companion object {
        const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        val ALLOWED_IMAGE_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/heic",
            "image/heif",
            "image/webp",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MealsRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMealsDataSource(implementation: MealsRepository): MealsDataSource
}
