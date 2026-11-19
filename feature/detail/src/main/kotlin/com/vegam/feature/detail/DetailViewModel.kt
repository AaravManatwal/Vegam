package com.vegam.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.database.DownloadStatus
import com.vegam.core.download.DownloadItem
import com.vegam.core.download.DownloadRepository
import com.vegam.engine.Checksum
import com.vegam.engine.Segment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Ek segment ka UI roop: 0..1 progress. */
data class SegmentUi(val index: Int, val progress: Float, val done: Boolean)

fun segmentUi(s: Segment): SegmentUi {
    val length = s.end - s.start + 1
    val p = if (length > 0) (s.downloaded.toFloat() / length).coerceIn(0f, 1f) else 1f
    return SegmentUi(s.index, p, s.isDone)
}

/** Speed graph ka upar wala limit: agla "gol" number (128KB/s ... 128MB/s), taaki axis stable rahe. */
fun niceMax(maxBytesPerSec: Long): Long {
    val steps = listOf(128L, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072).map { it * 1024 }
    return steps.firstOrNull { it >= maxBytesPerSec } ?: ((maxBytesPerSec / steps.last()) + 1) * steps.last()
}

sealed interface ChecksumState {
    data object Idle : ChecksumState
    data object Computing : ChecksumState
    data class Done(val hex: String) : ChecksumState
    data object Unavailable : ChecksumState
}

data class DetailUiState(
    val loaded: Boolean = false,
    val item: DownloadItem? = null,
    val segments: List<SegmentUi> = emptyList(),
    val speedHistory: List<Long> = emptyList(),
    val checksum: ChecksumState = ChecksumState.Idle,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DownloadRepository,
) : ViewModel() {
    private val id: Long = checkNotNull(savedStateHandle.get<Long>("id")) { "detail screen ko id chahiye" }
    private val checksum = MutableStateFlow<ChecksumState>(ChecksumState.Idle)

    val state: StateFlow<DetailUiState> = combine(
        repo.observe(id), repo.observeSegments(id), repo.observeSpeedHistory(id), checksum,
    ) { item, segs, hist, cs -> DetailUiState(true, item, segs.map(::segmentUi), hist, cs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun togglePause() {
        val item = state.value.item ?: return
        viewModelScope.launch {
            when (item.status) {
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> repo.resume(id)   // failed par "Retry"
                DownloadStatus.QUEUED, DownloadStatus.RUNNING -> repo.pause(id)
                else -> Unit
            }
        }
    }

    fun cancel(onDone: () -> Unit) {
        viewModelScope.launch { repo.cancel(id); onDone() }
    }

    /** SHA-256 background thread par: badi file mein kuch second lagte hain. */
    fun computeChecksum() {
        val item = state.value.item ?: return
        checksum.value = ChecksumState.Computing
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(item.targetPath)
            checksum.value = if (f.exists()) ChecksumState.Done(Checksum.sha256(f)) else ChecksumState.Unavailable
        }
    }
}
