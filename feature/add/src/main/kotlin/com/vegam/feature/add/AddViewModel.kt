package com.vegam.feature.add

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.download.DownloadRepository
import com.vegam.engine.FileInfo
import com.vegam.engine.UrlNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Server se metadata ka haal. */
sealed interface MetaState {
    data object Idle : MetaState
    data object Loading : MetaState
    data class Ready(val info: FileInfo) : MetaState
    data class Error(val message: String) : MetaState
}

data class AddUiState(
    val url: String = "",
    val name: String = "",
    val meta: MetaState = MetaState.Idle,
    val segments: Int = 8,
    val priority: Int = 1,              // 0 low, 1 normal, 2 high
    val wifiOnly: Boolean = false,
    val scheduledAt: Long? = null,      // null = abhi
    val folderLabel: String = "App folder (private)",
) {
    val urlValid: Boolean get() = UrlNames.isHttpUrl(url)

    /** Server Range support na kare to 1 hi connection chalega, slider ka koi matlab nahi. */
    val rangeSupported: Boolean get() = (meta as? MetaState.Ready)?.info?.acceptRanges != false

    /** Metadata fail ho tab bhi start ho sakta hai (kuch servers HEAD block karte hain), sirf loading mein nahi. */
    val canStart: Boolean get() = urlValid && name.isNotBlank() && meta !is MetaState.Loading
}

@HiltViewModel
class AddViewModel @Inject constructor(private val repo: DownloadRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddUiState())
    val state: StateFlow<AddUiState> = _state.asStateFlow()
    private var probeJob: Job? = null

    /** Sheet khulne par: purana state saaf (ViewModel sheet band hone ke baad bhi zinda rehta hai). */
    fun reset(prefillUrl: String? = null) {
        probeJob?.cancel()
        _state.value = AddUiState(segments = repo.defaultSegments, wifiOnly = repo.wifiOnlyDefault, folderLabel = repo.saveLocation.label())
        if (!prefillUrl.isNullOrBlank()) onUrlChange(prefillUrl)
    }

    fun onUrlChange(raw: String) {
        val url = raw.trim()
        val valid = UrlNames.isHttpUrl(url)
        _state.update { it.copy(url = url, name = if (valid) UrlNames.fileName(url) else "", meta = if (valid) MetaState.Loading else MetaState.Idle) }
        probeJob?.cancel()
        if (!valid) return
        probeJob = viewModelScope.launch {
            delay(500)   // har akshar par request nahi: typing rukne ka wait
            val result = repo.probe(url)
            _state.update { s ->
                if (s.url != url) s   // user ne is beech link badal diya
                else s.copy(
                    meta = result.fold({ MetaState.Ready(it) }, { MetaState.Error(it.message ?: "Server se info nahi mili") }),
                    segments = if (result.getOrNull()?.acceptRanges == false) 1 else s.segments,
                )
            }
        }
    }

    fun onNameChange(name: String) = _state.update { it.copy(name = name) }
    fun onSegments(n: Int) = _state.update { it.copy(segments = n.coerceIn(1, 16)) }
    fun onPriority(p: Int) = _state.update { it.copy(priority = p) }
    fun onWifiOnly(v: Boolean) = _state.update { it.copy(wifiOnly = v) }
    fun onSchedule(at: Long?) = _state.update { it.copy(scheduledAt = at) }

    /** SAF picker se folder mila (permission activity-side par persist ho chuki hai). */
    fun onFolderPicked(uri: Uri) {
        repo.saveLocation.treeUri = uri.toString()
        _state.update { it.copy(folderLabel = repo.saveLocation.label()) }
    }

    fun useAppFolder() {
        repo.saveLocation.treeUri = null
        _state.update { it.copy(folderLabel = repo.saveLocation.label()) }
    }

    fun start(onAdded: () -> Unit) {
        val s = _state.value
        if (!s.canStart) return
        viewModelScope.launch {
            repo.add(s.url, s.name.trim(), s.priority, s.wifiOnly, if (s.rangeSupported) s.segments else 1, s.scheduledAt)
            onAdded()
        }
    }
}
