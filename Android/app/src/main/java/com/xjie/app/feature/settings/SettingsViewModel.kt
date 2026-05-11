package com.xjie.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.GlucoseUnit
import com.xjie.app.core.model.UserInfo
import com.xjie.app.core.model.UserSettings
import com.xjie.app.core.network.ApiException
import com.xjie.app.core.storage.PreferencesStore
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
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    prefs: PreferencesStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    val glucoseUnit: StateFlow<GlucoseUnit> = prefs.glucoseUnit
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlucoseUnit.MMOL)
    val omicsDemo: StateFlow<Boolean> = prefs.omicsDemoEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val u = repo.me()
        val s = repo.settings()
        _state.update { it.copy(loading = false, user = u, settings = s) }
    }

    fun updateLevel(level: String) = launchOp { _state.update { it.copy(settings = repo.updateLevel(level)) } }
    fun updateGlucoseUnit(u: GlucoseUnit) = launchOp { _state.update { st -> st.copy(settings = repo.updateGlucoseUnit(u)) }; load() }
    fun toggleAiChat() = launchOp { repo.toggleAiChat(state.value.user?.consent?.allow_ai_chat ?: false); load() }
    fun toggleDataUpload() = launchOp { repo.toggleDataUpload(state.value.user?.consent?.allow_data_upload ?: false); load() }
    fun toggleOmicsDemo(v: Boolean) = viewModelScope.launch { repo.setOmicsDemo(v) }
    fun showLogoutAlert(v: Boolean) = _state.update { it.copy(showLogoutAlert = v) }
    fun showProfileEdit(v: Boolean) = _state.update { it.copy(showProfileEdit = v) }
    fun updateProfile(
        sex: String?,
        age: Int?,
        heightCm: Double?,
        weightKg: Double?,
    ) = launchOp {
        repo.updateProfile(sex, age, heightCm, weightKg)
        _state.update { it.copy(showProfileEdit = false) }
        load()
    }
    fun confirmLogout() = viewModelScope.launch { repo.logout() }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun launchOp(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { e ->
            _state.update { it.copy(error = (e as? ApiException)?.message ?: e.message) }
        }
    }
}
