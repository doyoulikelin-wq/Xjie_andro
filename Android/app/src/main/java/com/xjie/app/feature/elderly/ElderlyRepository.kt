package com.xjie.app.feature.elderly

import com.xjie.app.core.auth.AuthManager
import com.xjie.app.core.model.ElderlyCheckin
import com.xjie.app.core.model.ElderlyCheckinBody
import com.xjie.app.core.model.ElderlyCheckinList
import com.xjie.app.core.model.ElderlyTodayStatus
import com.xjie.app.core.model.SimpleOk
import com.xjie.app.core.network.api.ElderlyApi
import com.xjie.app.core.network.safeApiCall
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ElderlyRepository @Inject constructor(
    private val api: ElderlyApi,
    private val json: Json,
) {
    suspend fun today(): ElderlyTodayStatus = safeApiCall(json) { api.today() }
    suspend fun create(body: ElderlyCheckinBody): ElderlyCheckin = safeApiCall(json) { api.create(body) }
    suspend fun list(days: Int = 30, limit: Int = 100): ElderlyCheckinList =
        safeApiCall(json) { api.list(days, limit) }
    suspend fun list(
        owner: AuthManager.AccountScopeSnapshot,
        days: Int = 30,
        limit: Int = 100,
    ): ElderlyCheckinList = safeApiCall(json) { api.listForOwner(owner, days, limit) }
    suspend fun delete(id: Long): SimpleOk = safeApiCall(json) { api.delete(id) }
}
