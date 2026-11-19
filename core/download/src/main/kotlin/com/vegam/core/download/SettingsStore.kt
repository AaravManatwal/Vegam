package com.vegam.core.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vegam_settings")

/** AppSettings ka DataStore roop. Padhna Flow se, likhna `update { it.copy(...) }` se. */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<AppSettings> = store.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }   // corrupt file par defaults
        .map { it.toSettings() }

    suspend fun update(change: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val cur = p.toSettings()
            val n = change(cur).sanitized()
            p[THEME] = n.themeMode.name; p[DYNAMIC] = n.dynamicColor; p[PARALLEL] = n.maxParallel
            p[SEGMENTS] = n.defaultSegments; p[LIMIT] = n.speedLimitMbps; p[WIFI] = n.wifiOnlyDefault
            p[CHARGING] = n.chargingOnly; p[CLIP] = n.clipboardWatch; p[ONB] = n.onboardingDone
            p[S_ANALYSIS] = n.smartAnalysis; p[S_DUP] = n.duplicateDetection; p[S_VALID] = n.linkValidation
            p[S_SEC] = n.securityCheck; p[S_NOTIF] = n.smartNotifications; p[S_HIST] = n.detectionHistory
            p[S_BG] = n.backgroundAnalysis
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        themeMode = runCatching { ThemeMode.valueOf(this[THEME] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = this[DYNAMIC] ?: false,
        maxParallel = this[PARALLEL] ?: 3,
        defaultSegments = this[SEGMENTS] ?: 8,
        speedLimitMbps = this[LIMIT] ?: 0,
        wifiOnlyDefault = this[WIFI] ?: false,
        chargingOnly = this[CHARGING] ?: false,
        clipboardWatch = this[CLIP] ?: true,
        onboardingDone = this[ONB] ?: false,
        smartAnalysis = this[S_ANALYSIS] ?: true,
        duplicateDetection = this[S_DUP] ?: true,
        linkValidation = this[S_VALID] ?: true,
        securityCheck = this[S_SEC] ?: true,
        smartNotifications = this[S_NOTIF] ?: true,
        detectionHistory = this[S_HIST] ?: true,
        backgroundAnalysis = this[S_BG] ?: true,
    ).sanitized()

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val PARALLEL = intPreferencesKey("max_parallel")
        val SEGMENTS = intPreferencesKey("default_segments")
        val LIMIT = intPreferencesKey("speed_limit_mbps")
        val WIFI = booleanPreferencesKey("wifi_only_default")
        val CHARGING = booleanPreferencesKey("charging_only")
        val CLIP = booleanPreferencesKey("clipboard_watch")
        val ONB = booleanPreferencesKey("onboarding_done")
        val S_ANALYSIS = booleanPreferencesKey("smart_analysis")
        val S_DUP = booleanPreferencesKey("smart_duplicate")
        val S_VALID = booleanPreferencesKey("smart_validation")
        val S_SEC = booleanPreferencesKey("smart_security")
        val S_NOTIF = booleanPreferencesKey("smart_notifications")
        val S_HIST = booleanPreferencesKey("smart_history")
        val S_BG = booleanPreferencesKey("smart_background")
    }
}
