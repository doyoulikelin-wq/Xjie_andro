package com.xjie.app.feature.omics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xjie.app.core.model.GenomicsDemoPanel
import com.xjie.app.core.model.MetabolomicsDemoPanel
import com.xjie.app.core.model.MicrobiomeDemoPanel
import com.xjie.app.core.model.OmicsTriadInsight
import com.xjie.app.core.model.ProteomicsDemoPanel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OmicsTab(val label: String) {
    Proteomics("蛋白组学"),
    Metabolomics("代谢组学"),
    Genomics("基因组学"),
}

data class OmicsUiState(
    val loading: Boolean = false,
    val tab: OmicsTab = OmicsTab.Proteomics,
    val metabolomics: MetabolomicsDemoPanel? = null,
    val proteomics: ProteomicsDemoPanel? = null,
    val genomics: GenomicsDemoPanel? = null,
    val microbiome: MicrobiomeDemoPanel? = null,
    val triad: OmicsTriadInsight? = null,
)

@HiltViewModel
class OmicsViewModel @Inject constructor(
    private val repo: OmicsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OmicsUiState())
    val state: StateFlow<OmicsUiState> = _state.asStateFlow()

    fun setTab(t: OmicsTab) = _state.update { it.copy(tab = t) }

    fun fetchAll() = viewModelScope.launch {
        _state.update { it.copy(loading = true) }
        val dM = async { repo.metabolomics() }
        val dP = async { repo.proteomics() }
        val dG = async { repo.genomics() }
        val dMb = async { repo.microbiome() }
        val dT = async { repo.triad() }
        _state.update {
            it.copy(
                loading = false,
                metabolomics = dM.await(),
                proteomics = dP.await(),
                genomics = dG.await(),
                microbiome = dMb.await(),
                triad = dT.await(),
            )
        }
    }
}
