package com.vegam.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.download.AppSettings
import com.vegam.core.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Activity ke liye: theme + onboarding gate. null = DataStore abhi padha nahi (onboarding ka flash nahi dikhana). */
@HiltViewModel
class MainViewModel @Inject constructor(repo: DownloadRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = repo.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
