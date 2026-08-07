package com.xjie.app.feature.medicalassistant

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.MedicalAssistantOverview
import com.xjie.app.core.network.api.HealthDataApi
import com.xjie.app.core.network.safeApiCall
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

interface MedicalAssistantRepository {
    suspend fun fetchOverview(owner: AuthManager.AccountScopeSnapshot): MedicalAssistantOverview
    suspend fun generateOverview(owner: AuthManager.AccountScopeSnapshot): MedicalAssistantOverview
}

@Singleton
class NetworkMedicalAssistantRepository @Inject constructor(
    private val api: HealthDataApi,
    private val json: Json,
) : MedicalAssistantRepository {
    override suspend fun fetchOverview(owner: AuthManager.AccountScopeSnapshot): MedicalAssistantOverview =
        safeApiCall(json) { api.medicalAssistantOverview(owner) }

    override suspend fun generateOverview(
        owner: AuthManager.AccountScopeSnapshot,
    ): MedicalAssistantOverview = safeApiCall(json) { api.generateMedicalAssistantOverview(owner) }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MedicalAssistantRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMedicalAssistantRepository(
        implementation: NetworkMedicalAssistantRepository,
    ): MedicalAssistantRepository
}
