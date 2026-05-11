package com.xjie.app.feature.meals

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.MealItem
import com.xjie.app.core.model.MealPhoto
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MealsUiState(
    val loading: Boolean = false,
    val uploading: Boolean = false,
    val meals: List<MealItem> = emptyList(),
    val photos: List<MealPhoto> = emptyList(),
    val showManualInput: Boolean = false,
    val manualKcal: String = "",
    val error: String? = null,
    val toast: String? = null,
)

@HiltViewModel
class MealsViewModel @Inject constructor(
    private val repo: MealsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MealsUiState())
    val state: StateFlow<MealsUiState> = _state.asStateFlow()

    fun fetchData() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val meals = runCatching { repo.list() }.getOrElse {
                AppLogger.data.w(it, "list meals"); emptyList()
            }
            val photos = runCatching { repo.photos() }.getOrElse {
                AppLogger.data.w(it, "list photos"); emptyList()
            }
            _state.update { it.copy(loading = false, meals = meals, photos = photos) }
        }
    }

    fun openManualInput() = _state.update { it.copy(showManualInput = true) }
    fun dismissManualInput() = _state.update { it.copy(showManualInput = false, manualKcal = "") }
    fun setManualKcal(s: String) = _state.update { it.copy(manualKcal = s.filter { c -> c.isDigit() }) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }
    fun showError(msg: String) = _state.update { it.copy(error = msg) }

    fun deleteMeal(mealId: String) {
        viewModelScope.launch {
            runCatching { repo.deleteMeal(mealId) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "\u5220\u9664\u5931\u8d25") } }
                .onSuccess { _state.update { it.copy(toast = "\u5df2\u5220\u9664") }; fetchData() }
        }
    }

    fun updateMeal(mealId: String, kcal: Int?, notes: String?) {
        viewModelScope.launch {
            runCatching { repo.updateMeal(mealId, kcal, notes) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "\u4fee\u6539\u5931\u8d25") } }
                .onSuccess { _state.update { it.copy(toast = "\u5df2\u4fee\u6539") }; fetchData() }
        }
    }

    fun submitManual() {
        val kcal = _state.value.manualKcal.toIntOrNull()
        if (kcal == null || kcal <= 0) {
            _state.update { it.copy(error = "请输入有效的热量数值") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(showManualInput = false, manualKcal = "") }
            runCatching { repo.createManual(kcal) }
                .onFailure { e ->
                    _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) }
                }
                .onSuccess { _state.update { it.copy(toast = "已记录") }; fetchData() }
        }
    }

    fun uploadPhoto(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            runCatching { repo.uploadPhoto(uri) }
                .onFailure { e ->
                    _state.update { it.copy(uploading = false, error = e.message ?: "上传失败") }
                }
                .onSuccess { photo ->
                    val isFood = isLikelyFood(photo)
                    if (!isFood) {
                        _state.update {
                            it.copy(
                                uploading = false,
                                error = "未识别到食物，请拍摄含有餐号的照片。",
                            )
                        }
                    } else {
                        _state.update { it.copy(uploading = false, toast = "上传成功") }
                    }
                    fetchData()
                }
        }
    }

    private fun isLikelyFood(photo: com.xjie.app.core.model.MealPhoto): Boolean {
        // \u4f18\u5148\u770b\u540e\u7aef\u660e\u786e\u7684 is_food \u5224\u5b9a
        photo.vision_json?.is_food?.let { return it }
        if (photo.status == "failed") return false
        val totalKcal = photo.calorie_estimate_kcal ?: 0.0
        return totalKcal > 0
    }
}
