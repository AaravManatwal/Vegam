package com.vegam.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.download.AppSettings
import com.vegam.core.download.DownloadRepository
import com.vegam.core.download.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(val settings: AppSettings = AppSettings(), val folderLabel: String = "")

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: DownloadRepository) : ViewModel() {
    private val folder = MutableStateFlow(repo.saveLocation.label())

    val state: StateFlow<SettingsUiState> = combine(repo.appSettings, folder) { s, f -> SettingsUiState(s, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(folderLabel = folder.value))

    private fun change(f: (AppSettings) -> AppSettings) { viewModelScope.launch { repo.updateSettings(f) } }

    fun setTheme(m: ThemeMode) = change { it.copy(themeMode = m) }
    fun setDynamic(v: Boolean) = change { it.copy(dynamicColor = v) }
    fun setMaxParallel(v: Int) = change { it.copy(maxParallel = v) }
    fun setSegments(v: Int) = change { it.copy(defaultSegments = v) }
    fun setSpeedLimit(mbps: Int) = change { it.copy(speedLimitMbps = mbps) }
    fun setWifiDefault(v: Boolean) = change { it.copy(wifiOnlyDefault = v) }
    fun setClipboard(v: Boolean) = change { it.copy(clipboardWatch = v) }
    fun setCharging(v: Boolean) = change { it.copy(chargingOnly = v) }
    fun setSmartAnalysis(v: Boolean) = change { it.copy(smartAnalysis = v) }
    fun setDuplicate(v: Boolean) = change { it.copy(duplicateDetection = v) }
    fun setValidation(v: Boolean) = change { it.copy(linkValidation = v) }
    fun setSecurity(v: Boolean) = change { it.copy(securityCheck = v) }
    fun setSmartNotif(v: Boolean) = change { it.copy(smartNotifications = v) }
    fun setHistory(v: Boolean) = change { it.copy(detectionHistory = v) }
    fun setBackground(v: Boolean) = change { it.copy(backgroundAnalysis = v) }
    fun replayOnboarding() = change { it.copy(onboardingDone = false) }

    fun onFolderPicked(uri: Uri) { repo.saveLocation.treeUri = uri.toString(); folder.value = repo.saveLocation.label() }
    fun useAppFolder() { repo.saveLocation.treeUri = null; folder.value = repo.saveLocation.label() }
}
