package com.prodm.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val theme: Int = 0,            // 0 system, 1 light, 2 dark
    val amoled: Boolean = false,
    val dynamic: Boolean = false,
    val maxConcurrent: Int = 3,
    val connections: Int = 8,
    val wifiOnly: Boolean = false,
    val speedLimitKb: Int = 0,
    val clipboard: Boolean = true,
    val scheduleOn: Boolean = false,
    val schedHour: Int = 2,
    val schedMinute: Int = 0
)

class SettingsRepo(private val ctx: Context) {
    private object K {
        val THEME = intPreferencesKey("theme")
        val AMOLED = booleanPreferencesKey("amoled")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val MAX = intPreferencesKey("max_concurrent")
        val CONN = intPreferencesKey("connections")
        val WIFI = booleanPreferencesKey("wifi_only")
        val LIMIT = intPreferencesKey("speed_limit")
        val CLIP = booleanPreferencesKey("clipboard")
        val SCHED = booleanPreferencesKey("sched_on")
        val SH = intPreferencesKey("sched_h")
        val SM = intPreferencesKey("sched_m")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val flow: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        val d = AppSettings()
        AppSettings(
            theme = p[K.THEME] ?: d.theme,
            amoled = p[K.AMOLED] ?: d.amoled,
            dynamic = p[K.DYNAMIC] ?: d.dynamic,
            maxConcurrent = p[K.MAX] ?: d.maxConcurrent,
            connections = p[K.CONN] ?: d.connections,
            wifiOnly = p[K.WIFI] ?: d.wifiOnly,
            speedLimitKb = p[K.LIMIT] ?: d.speedLimitKb,
            clipboard = p[K.CLIP] ?: d.clipboard,
            scheduleOn = p[K.SCHED] ?: d.scheduleOn,
            schedHour = p[K.SH] ?: d.schedHour,
            schedMinute = p[K.SM] ?: d.schedMinute
        )
    }

    val current: StateFlow<AppSettings> = flow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun snapshot(): AppSettings = flow.first()

    suspend fun update(block: (AppSettings) -> AppSettings) {
        val n = block(snapshot())
        ctx.dataStore.edit { p ->
            p[K.THEME] = n.theme
            p[K.AMOLED] = n.amoled
            p[K.DYNAMIC] = n.dynamic
            p[K.MAX] = n.maxConcurrent
            p[K.CONN] = n.connections
            p[K.WIFI] = n.wifiOnly
            p[K.LIMIT] = n.speedLimitKb
            p[K.CLIP] = n.clipboard
            p[K.SCHED] = n.scheduleOn
            p[K.SH] = n.schedHour
            p[K.SM] = n.schedMinute
        }
    }
}
