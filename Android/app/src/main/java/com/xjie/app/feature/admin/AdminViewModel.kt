package com.xjie.app.feature.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.AdminFeatureFlag
import com.xjie.app.core.network.api.AdminConversationItem
import com.xjie.app.core.network.api.AdminOmicsItem
import com.xjie.app.core.network.api.AdminStats
import com.xjie.app.core.network.api.AdminTokenStats
import com.xjie.app.core.network.api.AdminUserItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminTab(val label: String) {
    Overview("总览"),
    Users("用户"),
    Conversations("会话"),
    Omics("组学"),
    Tokens("Token"),
    FeatureFlags("特性开关"),
}

data class AdminUiState(
    val loading: Boolean = false,
    val tab: AdminTab = AdminTab.Overview,
    val stats: AdminStats? = null,
    val users: List<AdminUserItem> = emptyList(),
    val conversations: List<AdminConversationItem> = emptyList(),
    val omics: List<AdminOmicsItem> = emptyList(),
    val tokenStats: AdminTokenStats? = null,
    val featureFlags: List<AdminFeatureFlag> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repo: AdminRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    fun setTab(t: AdminTab) {
        _state.update { it.copy(tab = t) }
        when (t) {
            AdminTab.Users -> if (_state.value.users.isEmpty()) loadUsers()
            AdminTab.Conversations -> if (_state.value.conversations.isEmpty()) loadConversations()
            AdminTab.Omics -> if (_state.value.omics.isEmpty()) loadOmics()
            AdminTab.Tokens -> if (_state.value.tokenStats == null) loadTokens()
            AdminTab.FeatureFlags -> if (_state.value.featureFlags.isEmpty()) loadFlags()
            else -> {}
        }
    }

    fun loadOverview() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val s = repo.stats()
        _state.update { it.copy(loading = false, stats = s) }
    }

    fun loadUsers() = viewModelScope.launch {
        val list = repo.users()
        _state.update { it.copy(users = list) }
    }

    fun loadConversations() = viewModelScope.launch {
        val list = repo.conversations()
        _state.update { it.copy(conversations = list) }
    }

    fun loadOmics() = viewModelScope.launch {
        val list = repo.omics()
        _state.update { it.copy(omics = list) }
    }

    fun loadTokens() = viewModelScope.launch {
        val s = repo.tokenStats()
        _state.update { it.copy(tokenStats = s) }
    }

    fun loadFlags() = viewModelScope.launch {
        val list = repo.listFeatureFlags()
        _state.update { it.copy(featureFlags = list) }
    }

    fun toggleFlag(flag: AdminFeatureFlag) = viewModelScope.launch {
        runCatching { repo.toggleFeatureFlag(flag.id, !flag.enabled) }
            .onSuccess { updated ->
                _state.update { st ->
                    st.copy(featureFlags = st.featureFlags.map {
                        if (it.id == updated.id) updated else it
                    })
                }
            }
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
