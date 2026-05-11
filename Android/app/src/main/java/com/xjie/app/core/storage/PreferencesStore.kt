package com.xjie.app.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.xjie.app.core.model.GlucoseUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "xjie_prefs")

/** 偏好设置：血糖单位、Demo 开关 —— 对应 iOS [Units.swift] / [DemoSettings.swift] */
@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private object Keys {
        val GLUCOSE_UNIT = stringPreferencesKey("xjie.glucoseUnit")
        val OMICS_DEMO = booleanPreferencesKey("xjie.omicsDemo")
    }

    val glucoseUnit: Flow<GlucoseUnit> = ctx.dataStore.data.map { p ->
        GlucoseUnit.fromRaw(p[Keys.GLUCOSE_UNIT])
    }

    suspend fun setGlucoseUnit(unit: GlucoseUnit) {
        ctx.dataStore.edit { it[Keys.GLUCOSE_UNIT] = unit.raw }
    }

    val omicsDemoEnabled: Flow<Boolean> = ctx.dataStore.data.map { p ->
        p[Keys.OMICS_DEMO] ?: true   // 默认开启
    }

    suspend fun setOmicsDemoEnabled(v: Boolean) {
        ctx.dataStore.edit { it[Keys.OMICS_DEMO] = v }
    }
}
