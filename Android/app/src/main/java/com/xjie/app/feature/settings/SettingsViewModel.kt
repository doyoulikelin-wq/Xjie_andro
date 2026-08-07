package com.xjie.app.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.model.UserSettings
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.storage.PreferencesStore
import com.xjie.app.feature.medication.MedicationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = false,
    val user: UserInfo? = null,
    val settings: UserSettings? = null,
    val showLogoutAlert: Boolean = false,
    val showProfileEdit: Boolean = false,
    val feedbackSubmitting: Boolean = false,
    val deleteSubmitting: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    prefs: PreferencesStore,
    private val scheduler: MedicationScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val glucoseUnit: StateFlow<GlucoseUnit> = prefs.glucoseUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlucoseUnit.MMOL)
    val omicsDemo: StateFlow<Boolean> = prefs.omicsDemoEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun load() {
        val owner = captureOwnerOrReport() ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repo.me(owner) to repo.settings(owner)
            }.onSuccess { (user, settings) ->
                if (!repo.isCurrent(owner)) return@onSuccess
                _state.update { it.copy(loading = false, user = user, settings = settings) }
                // 只有同一账号代次仍有效时才允许重排本机提醒。
                if (settings != null && repo.isCurrent(owner)) {
                    scheduler.scheduleElderlyReminders(
                        settings.elderly_checkin_interval_min,
                        settings.elderly_mode,
                    )
                }
            }.onFailure { error -> commitAccountErrorIfCurrent(owner, error) }
        }
    }

    fun updateLevel(level: String) = launchAccountOp { owner ->
        val settings = repo.updateLevel(owner, level)
        commitIfCurrent(owner) { it.copy(settings = settings) }
    }

    fun updateGlucoseUnit(u: GlucoseUnit) = launchAccountOp { owner ->
        val settings = repo.updateGlucoseUnit(owner, u)
        commitIfCurrent(owner) { it.copy(settings = settings) }
    }

    fun updateElderlyMode(enabled: Boolean) = launchAccountOp { owner ->
        val settings = repo.updateElderlyMode(owner, enabled)
        if (!repo.isCurrent(owner)) return@launchAccountOp
        _state.update { it.copy(settings = settings) }
        scheduler.scheduleElderlyReminders(settings.elderly_checkin_interval_min, settings.elderly_mode)
    }

    fun updateElderlyInterval(min: Int) = launchAccountOp { owner ->
        val settings = repo.updateElderlyInterval(owner, min)
        if (!repo.isCurrent(owner)) return@launchAccountOp
        _state.update { it.copy(settings = settings) }
        scheduler.scheduleElderlyReminders(settings.elderly_checkin_interval_min, settings.elderly_mode)
    }

    fun toggleAiChat() = launchAccountOp { owner ->
        repo.toggleAiChat(owner, state.value.user?.consent?.allow_ai_chat ?: false)
        if (repo.isCurrent(owner)) load()
    }

    fun toggleDataUpload() = launchAccountOp { owner ->
        repo.toggleDataUpload(owner, state.value.user?.consent?.allow_data_upload ?: false)
        if (repo.isCurrent(owner)) load()
    }
    fun toggleOmicsDemo(v: Boolean) = viewModelScope.launch { repo.setOmicsDemo(v) }
    fun submitFeedback(
        category: String,
        content: String,
        contact: String?,
        onSuccess: () -> Unit = {},
    ) {
        if (state.value.feedbackSubmitting) return
        val owner = captureOwnerOrReport() ?: return
        _state.update { it.copy(feedbackSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.submitFeedback(owner, category, content, contact) }
                .onSuccess {
                    if (!repo.isCurrent(owner)) return@onSuccess
                    _state.update { it.copy(feedbackSubmitting = false, message = "反馈已提交") }
                    onSuccess()
                }
                .onFailure { error ->
                    if (!repo.isCurrent(owner)) return@onFailure
                    _state.update {
                        it.copy(
                            feedbackSubmitting = false,
                            error = (error as? ApiException)?.message
                                ?: error.message
                                ?: "提交失败",
                        )
                    }
                }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit = {}) {
        if (state.value.deleteSubmitting) return
        val owner = captureOwnerOrReport() ?: return
        _state.update { it.copy(deleteSubmitting = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.deleteAccount(owner) }
                .onSuccess { clearedCurrentSession ->
                    if (clearedCurrentSession) onSuccess()
                }
                .onFailure { error ->
                    if (!repo.isCurrent(owner)) return@onFailure
                    _state.update {
                        it.copy(
                            deleteSubmitting = false,
                            error = (error as? ApiException)?.message
                                ?: error.message
                                ?: "注销失败",
                        )
                    }
                }
        }
    }
    fun showLogoutAlert(v: Boolean) = _state.update { it.copy(showLogoutAlert = v) }
    fun showProfileEdit(v: Boolean) = _state.update { it.copy(showProfileEdit = v) }
    fun updateProfile(
        sex: String?,
        age: Int?,
        heightCm: Double?,
        weightKg: Double?,
        displayName: String? = null,
    ) {
        val owner = captureOwnerOrReport() ?: return
        Log.i("XJieProfile", "PATCH request sex=$sex age=$age h=$heightCm w=$weightKg name=$displayName")
        viewModelScope.launch {
            runCatching {
                repo.updateProfile(owner, sex, age, heightCm, weightKg, displayName)
            }.onSuccess { updated ->
                if (!repo.isCurrent(owner)) return@onSuccess
                Log.i("XJieProfile", "PATCH success -> $updated")
                // 直接以 PATCH 返回的 profile 更新本地状态，避免 me() 二次拉取导致"跳回原始数据"
                _state.update { st ->
                    val user = st.user?.copy(profile = updated)
                    st.copy(showProfileEdit = false, user = user, error = null)
                }
            }.onFailure { error ->
                if (!repo.isCurrent(owner)) return@onFailure
                Log.e("XJieProfile", "PATCH failed", error)
                _state.update {
                    it.copy(error = (error as? ApiException)?.message ?: error.message ?: "保存失败")
                }
            }
        }
    }

    fun confirmLogout() = repo.logout()
    fun clearError() = _state.update { it.copy(error = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun launchAccountOp(
        block: suspend (com.xjie.app.core.auth.AuthManager.AccountScopeSnapshot) -> Unit,
    ) {
        val owner = captureOwnerOrReport() ?: return
        viewModelScope.launch {
            runCatching { block(owner) }.onFailure { error ->
                if (!repo.isCurrent(owner)) return@onFailure
                _state.update {
                    it.copy(error = (error as? ApiException)?.message ?: error.message)
                }
            }
        }
    }

    private fun captureOwnerOrReport() = repo.captureOwner().also { owner ->
        if (owner == null) {
            _state.update {
                it.copy(
                    loading = false,
                    feedbackSubmitting = false,
                    deleteSubmitting = false,
                    error = "登录已失效，请重新登录后再试。",
                )
            }
        }
    }

    private fun commitIfCurrent(
        owner: com.xjie.app.core.auth.AuthManager.AccountScopeSnapshot,
        update: (SettingsUiState) -> SettingsUiState,
    ) {
        if (repo.isCurrent(owner)) _state.update(update)
    }

    private fun commitAccountErrorIfCurrent(
        owner: com.xjie.app.core.auth.AuthManager.AccountScopeSnapshot,
        error: Throwable,
    ) {
        if (!repo.isCurrent(owner)) return
        _state.update {
            it.copy(
                loading = false,
                error = (error as? ApiException)?.message ?: error.message ?: "读取设置失败",
            )
        }
    }
}
