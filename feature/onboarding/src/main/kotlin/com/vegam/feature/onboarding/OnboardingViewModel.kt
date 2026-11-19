package com.vegam.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vegam.core.download.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repo: DownloadRepository) : ViewModel() {
    fun finish() { viewModelScope.launch { repo.updateSettings { it.copy(onboardingDone = true) } } }
}
