package com.xjie.app.core.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.BuildConfig
import com.xjie.app.core.model.AppUpdateCheck
import com.xjie.app.core.network.api.AppUpdateApi
import com.xjie.app.core.util.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUpdateUiState(
    val pendingUpdate: AppUpdateCheck? = null,
    val dismissedBuild: Int? = null,
    val checking: Boolean = false,
    val installing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val api: AppUpdateApi,
) : ViewModel() {
    private val _state = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()
    private var checkedThisLaunch = false

    fun checkIfNeeded() {
        if (checkedThisLaunch) return
        checkedThisLaunch = true
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            runCatching {
                api.check("android", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            }.onSuccess { info ->
                val shouldShow = info.updateAvailable || info.shouldForce
                val dismissed = _state.value.dismissedBuild == info.latestBuild
                if (shouldShow && (info.shouldForce || !dismissed)) {
                    _state.update { it.copy(pendingUpdate = info, checking = false, error = null) }
                } else {
                    _state.update { it.copy(checking = false, error = null) }
                }
            }.onFailure { e ->
                AppLogger.network.w("App update check failed: ${e.message}")
                _state.update { it.copy(checking = false) }
            }
        }
    }

    fun dismiss(info: AppUpdateCheck) {
        if (info.shouldForce) return
        _state.update { it.copy(pendingUpdate = null, dismissedBuild = info.latestBuild) }
    }

    fun setInstalling(value: Boolean) {
        _state.update { it.copy(installing = value, error = null) }
    }

    fun setError(message: String) {
        _state.update { it.copy(installing = false, error = message) }
    }
}
