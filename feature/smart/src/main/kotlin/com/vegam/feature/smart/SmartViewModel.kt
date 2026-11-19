package com.vegam.feature.smart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.database.DetectionDao
import com.vegam.core.database.DetectionRow
import com.vegam.core.download.SmartCoordinator
import com.vegam.core.download.SmartSession
import com.vegam.core.download.SmartSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, DOWNLOADED, CANCELLED, BLOCKED, WARNINGS }

/** History row ka "natija" (download hua / fail hua / cancel...). Join se asli download status aata hai. */
fun DetectionRow.outcome(): String = when {
    d.decision == "BLOCKED" -> "Block"
    d.decision == "CANCEL" || d.decision == "ABANDONED" -> "Cancel"
    d.decision == "DOWNLOAD" -> when (downloadStatus) {
        "COMPLETED" -> "Download ho gaya"
        "FAILED" -> "Download fail" + (downloadError?.let { ": $it" } ?: "")
        null -> "Download list se hata diya"
        else -> "Download chal raha / line mein"
    }
    else -> "Faisla baaki"
}

fun filterRows(rows: List<DetectionRow>, query: String, f: HistoryFilter): List<DetectionRow> {
    val q = query.trim().lowercase()
    return rows.filter { r ->
        val okFilter = when (f) {
            HistoryFilter.ALL -> true
            HistoryFilter.DOWNLOADED -> r.d.decision == "DOWNLOAD"
            HistoryFilter.CANCELLED -> r.d.decision == "CANCEL" || r.d.decision == "ABANDONED"
            HistoryFilter.BLOCKED -> r.d.decision == "BLOCKED"
            HistoryFilter.WARNINGS -> r.d.securityLevel == "YELLOW" || r.d.securityLevel == "RED" || r.d.warnings.isNotBlank()
        }
        okFilter && (q.isEmpty() || r.d.fileName.lowercase().contains(q) || r.d.domain.lowercase().contains(q) || r.d.url.lowercase().contains(q))
    }
}

@HiltViewModel
class SmartViewModel @Inject constructor(
    private val coordinator: SmartCoordinator,
    private val dao: DetectionDao,
) : ViewModel() {
    val session: StateFlow<SmartSession?> = coordinator.session

    /** Extra confirmation (YELLOW / duplicate) ka doosra step. */
    private val _confirming = MutableStateFlow(false)
    val confirming: StateFlow<Boolean> = _confirming

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(HistoryFilter.ALL)
    val history: StateFlow<List<DetectionRow>> =
        combine(dao.observeAll(), query, filter) { rows, q, f -> filterRows(rows, q, f) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun submit(url: String, source: SmartSource) = coordinator.submit(url, source)
    fun reopen(url: String) = coordinator.reopen(url)
    fun cancel() { _confirming.value = false; coordinator.cancel() }
    fun back() { _confirming.value = false }

    /** Download dabaya: extra confirm baaki ho to pehle wo, warna seedha. */
    fun onDownload(s: SmartSession.Ready, onAdded: () -> Unit) {
        if (s.result.decision.extraConfirm.isNotEmpty() && !_confirming.value) { _confirming.update { true }; return }
        viewModelScope.launch {
            _confirming.value = false
            if (coordinator.download(s) != null) onAdded()
        }
    }

    fun delete(id: Long) { viewModelScope.launch { dao.delete(id) } }
    fun clearAll() { viewModelScope.launch { dao.clear() } }
}
