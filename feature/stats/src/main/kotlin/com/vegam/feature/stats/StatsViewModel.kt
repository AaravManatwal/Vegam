package com.vegam.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.download.DownloadRepository
import com.vegam.core.ui.fileKindOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(repo: DownloadRepository) : ViewModel() {
    val state: StateFlow<StatsUi> = repo.observeAll()
        .map { buildStats(it, System.currentTimeMillis(), ZoneId.systemDefault()) { name -> fileKindOf(name).label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())
}
