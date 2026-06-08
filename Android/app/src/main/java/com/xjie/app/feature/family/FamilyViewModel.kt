package com.xjie.app.feature.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.FamilyGroup
import com.xjie.app.core.model.FamilyInvite
import com.xjie.app.core.model.FamilyMember
import com.xjie.app.core.model.FamilyPermission
import com.xjie.app.core.model.FamilyPermissionField
import com.xjie.app.core.model.FamilySubject
import com.xjie.app.core.model.FamilySubjectSummary
import com.xjie.app.core.model.familyPermissionPatch
import com.xjie.app.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

data class FamilyUiState(
    val loading: Boolean = false,
    val groups: List<FamilyGroup> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val subjects: List<FamilySubject> = emptyList(),
    val selectedSubject: FamilySubject? = null,
    val selectedSummary: FamilySubjectSummary? = null,
    val permissionsByViewer: Map<Long, FamilyPermission> = emptyMap(),
    val latestInvite: FamilyInvite? = null,
    val currentUserId: Long? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class FamilyViewModel @Inject constructor(
    private val repo: FamilyRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FamilyUiState())
    val state: StateFlow<FamilyUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching {
            withTimeout(10_000) {
                val groups = repo.groups()
                val members = repo.members()
                val subjects = repo.subjects()
                val currentId = subjects.firstOrNull { it.member_id == null }?.user_id
                val selected = _state.value.selectedSubject?.let { old ->
                    subjects.firstOrNull { it.user_id == old.user_id }
                } ?: subjects.firstOrNull()
                val permissions = loadPermissions(members, currentId)
                val summary = selected?.let { repo.summary(it.user_id) }
                _state.update {
                    it.copy(
                        loading = false,
                        groups = groups,
                        members = members,
                        subjects = subjects,
                        selectedSubject = selected,
                        selectedSummary = summary,
                        permissionsByViewer = permissions,
                        currentUserId = currentId,
                        error = null,
                    )
                }
            }
        }.onFailure { e ->
            _state.update { it.copy(loading = false, error = e.userMessage()) }
        }
    }

    fun createInvite(phone: String?, relation: String?) = viewModelScope.launch {
        runCatching {
            val groupId = state.value.groups.firstOrNull()?.id ?: repo.createGroup().id
            repo.createInvite(groupId, phone, relation)
        }.onSuccess { invite ->
            _state.update { it.copy(latestInvite = invite, message = "邀请码已生成") }
            load()
        }.onFailure { e ->
            _state.update { it.copy(error = e.userMessage()) }
        }
    }

    fun acceptInvite(code: String, displayName: String?) = viewModelScope.launch {
        runCatching { repo.acceptInvite(code, displayName) }
            .onSuccess {
                _state.update { it.copy(message = "已加入家庭") }
                load()
            }
            .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
    }

    fun selectSubject(subject: FamilySubject) = viewModelScope.launch {
        _state.update { it.copy(selectedSubject = subject, loading = true) }
        runCatching { repo.summary(subject.user_id) }
            .onSuccess { summary -> _state.update { it.copy(loading = false, selectedSummary = summary) } }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
    }

    fun sendCareEvent(type: String, message: String?) = viewModelScope.launch {
        val subject = state.value.selectedSubject ?: return@launch
        runCatching { repo.createCareEvent(subject.user_id, type, message) }
            .onSuccess {
                _state.update { it.copy(message = "已记录关心提醒") }
                selectSubject(subject)
            }
            .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
    }

    fun permissionValue(viewerUserId: Long, field: FamilyPermissionField): Boolean {
        val current = state.value.currentUserId ?: 0
        val p = state.value.permissionsByViewer[viewerUserId] ?: FamilyPermission.empty(current, viewerUserId)
        return when (field) {
            FamilyPermissionField.GLUCOSE_DETAIL -> p.can_view_glucose_detail
            FamilyPermissionField.MEDICATION -> p.can_view_medication
            FamilyPermissionField.HEALTH_DATA -> p.can_view_health_data
            FamilyPermissionField.DOCUMENTS -> p.can_view_documents
            FamilyPermissionField.OMICS -> p.can_view_omics
            FamilyPermissionField.AI_SUMMARY -> p.can_view_ai_summary
        }
    }

    fun updatePermission(viewerUserId: Long, field: FamilyPermissionField, value: Boolean) = viewModelScope.launch {
        runCatching { repo.updatePermission(viewerUserId, familyPermissionPatch(field, value)) }
            .onSuccess { permission ->
                _state.update {
                    it.copy(
                        permissionsByViewer = it.permissionsByViewer + (viewerUserId to permission),
                        message = "授权已更新",
                    )
                }
            }
            .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    private suspend fun loadPermissions(
        members: List<FamilyMember>,
        currentUserId: Long?,
    ): Map<Long, FamilyPermission> {
        if (currentUserId == null) return emptyMap()
        return members
            .filter { it.user_id != currentUserId && it.status == "active" }
            .mapNotNull { member ->
                runCatching { member.user_id to repo.permission(member.user_id) }.getOrNull()
            }
            .toMap()
    }

    private fun Throwable.userMessage(): String = when (this) {
        is TimeoutCancellationException -> "网络响应较慢，请稍后重试"
        is ApiException -> message ?: "请求失败，请稍后重试"
        else -> message ?: "请求失败，请稍后重试"
    }
}
