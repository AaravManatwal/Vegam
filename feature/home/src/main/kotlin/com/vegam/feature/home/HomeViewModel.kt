package com.vegam.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import com.vegam.core.download.DownloadRepository
import com.vegam.engine.FuzzyMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeStats(val totalBytes: Long = 0, val speedNow: Long = 0, val storageBytes: Long = 0)

data class HomeUiState(
    val stats: HomeStats = HomeStats(),
    val active: List<DownloadItem> = emptyList(),
    val completed: List<DownloadItem> = emptyList(),
    val query: String = "",
)

/**
 * Pure function: items + search query -> screen state. ViewModel se alag rakha hai taaki
 * bina Android/coroutines ke unit test ho sake.
 */
fun buildHomeState(items: List<DownloadItem>, query: String): HomeUiState {
    val visible = items.filter { FuzzyMatch.matches(query, it.name) }
    val activeStates = setOf(DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED, DownloadStatus.FAILED)
    val active = visible.filter { it.status in activeStates }
        .sortedWith(compareByDescending<DownloadItem> { it.isRunning }.thenByDescending { it.priority }.thenBy { it.createdAt })
    val completed = visible.filter { it.status == DownloadStatus.COMPLETED }
        .sortedByDescending { it.completedAt ?: 0L }
    // Stats poori list par (search se filter nahi): CANCELLED ka partial data count nahi hota.
    val counted = items.filter { it.status != DownloadStatus.CANCELLED }
    val stats = HomeStats(
        totalBytes = items.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.totalBytes },
        speedNow = items.filter { it.isRunning }.sumOf { it.bytesPerSec },
        storageBytes = counted.sumOf { it.downloadedBytes },
    )
    return HomeUiState(stats, active, completed, query)
}

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: DownloadRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        combine(repo.observeAll(), query) { items, q -> buildHomeState(items, q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onQueryChange(q: String) { query.value = q }

    fun togglePause(item: DownloadItem) = viewModelScope.launch {
        when (item.status) {
            DownloadStatus.PAUSED, DownloadStatus.FAILED -> repo.resume(item.id)   // failed par "Retry"
            else -> repo.pause(item.id)
        }
    }

    fun delete(id: Long) { viewModelScope.launch { repo.delete(id) } }
}
