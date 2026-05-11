package com.xjie.app.feature.omics

import com.xjie.app.core.model.GenomicsDemoPanel
import com.xjie.app.core.model.MetabolomicsDemoPanel
import com.xjie.app.core.model.MicrobiomeDemoPanel
import com.xjie.app.core.model.OmicsTriadInsight
import com.xjie.app.core.model.ProteomicsDemoPanel
import com.xjie.app.core.network.api.OmicsApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmicsRepository @Inject constructor(
    private val api: OmicsApi,
    private val json: Json,
) {
    suspend fun metabolomics(): MetabolomicsDemoPanel? =
        runCatching { safeApiCall(json) { api.metabolomics() } }.getOrNull()
    suspend fun proteomics(): ProteomicsDemoPanel? =
        runCatching { safeApiCall(json) { api.proteomics() } }.getOrNull()
    suspend fun genomics(): GenomicsDemoPanel? =
        runCatching { safeApiCall(json) { api.genomics() } }.getOrNull()
    suspend fun microbiome(): MicrobiomeDemoPanel? =
        runCatching { safeApiCall(json) { api.microbiome() } }.getOrNull()
    suspend fun triad(): OmicsTriadInsight? =
        runCatching { safeApiCall(json) { api.triad() } }.getOrNull()
}
