package com.xjie.app.feature.patienthistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.PatientHistoryField
import com.xjie.app.core.model.PatientHistoryProfile
import com.xjie.app.core.model.PatientHistoryUpdateBody
import com.xjie.app.feature.healthdata.HealthDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class PatientHistoryUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val profile: PatientHistoryProfile = PatientHistoryProfile(sections = DEFAULT_SECTIONS),
    val error: String? = null,
    val toast: String? = null,
)

private val DEFAULT_SECTIONS = linkedMapOf(
    "diagnoses" to PatientHistoryField(),
    "surgeries" to PatientHistoryField(),
    "medications" to PatientHistoryField(),
    "allergies" to PatientHistoryField(),
    "recent_findings" to PatientHistoryField(source_type = "document"),
    "care_goals" to PatientHistoryField(),
    "family_history" to PatientHistoryField(),
    "lifestyle_risks" to PatientHistoryField(),
)

@HiltViewModel
class PatientHistoryViewModel @Inject constructor(
    private val repo: HealthDataRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PatientHistoryUiState())
    val state: StateFlow<PatientHistoryUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        runCatching { repo.patientHistory() }
            .onSuccess { profile ->
                _state.update {
                    it.copy(
                        loading = false,
                        profile = mergeDefaults(profile),
                    )
                }
            }
            .onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "加载失败") }
            }
    }

    fun save() = viewModelScope.launch {
        val current = _state.value
        if (current.loading || current.saving) return@launch

        val profile = current.profile
        _state.update { it.copy(saving = true) }
        val verifiedAt = if (profile.doctor_summary.isNotBlank() || profile.sections.values.any { it.verified_by_user }) {
            Instant.now().toString()
        } else {
            null
        }

        runCatching {
            repo.savePatientHistory(
                PatientHistoryUpdateBody(
                    doctor_summary = profile.doctor_summary.trim(),
                    sections = profile.sections,
                    verified_at = verifiedAt,
                ),
            )
        }.onSuccess { saved ->
            _state.update {
                it.copy(
                    saving = false,
                    profile = mergeDefaults(saved),
                    toast = "病史整理已保存",
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(saving = false, error = e.message ?: "保存失败") }
        }
    }

    fun updateDoctorSummary(value: String) {
        _state.update { it.copy(profile = it.profile.copy(doctor_summary = value)) }
    }

    fun updateSection(key: String, transform: (PatientHistoryField) -> PatientHistoryField) {
        _state.update {
            val currentField = it.profile.sections[key] ?: PatientHistoryField()
            val nextSections = LinkedHashMap(it.profile.sections)
            nextSections[key] = transform(currentField)
            it.copy(profile = it.profile.copy(sections = nextSections))
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }

    private fun mergeDefaults(profile: PatientHistoryProfile): PatientHistoryProfile {
        return profile.copy(sections = LinkedHashMap(DEFAULT_SECTIONS).apply { putAll(profile.sections) })
    }
}