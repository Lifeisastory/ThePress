package com.aster.feature.digest.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.data.repository.NewsDigestRepository
import com.aster.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class DigestUiState(
    val digest: NewsDigest? = null,
    val settings: DigestSettings? = null,
    val loading: Boolean = true,
    val readFailed: Boolean = false,
    val settingsFailed: Boolean = false,
    val generating: Boolean = false,
    val failure: DigestFailure? = null
)

internal class DigestViewModel(
    private val repository: NewsDigestRepository,
    settings: DigestSettingsRepository
) : ViewModel() {
    private val state = MutableStateFlow(DigestUiState())
    val uiState = state.asStateFlow()
    private var generation: Job? = null

    init {
        viewModelScope.launch {
            repository.observeLatestDigest().retryWhen { _, _ ->
                state.update { it.copy(readFailed = true, loading = false) }; delay(3000); true
            }.collect { digest -> state.update { it.copy(digest = digest, loading = false, readFailed = false) } }
        }
        viewModelScope.launch {
            settings.settings.retryWhen { _, _ ->
                state.update { it.copy(settingsFailed = true) }; delay(3000); true
            }.collect { config -> state.update { it.copy(settings = config, settingsFailed = false) } }
        }
    }

    fun generate() {
        if (state.value.generating) return
        state.update { it.copy(generating = true, failure = null) }
        generation = viewModelScope.launch {
            try {
                val result = repository.generateDigest()
                state.update { it.copy(failure = (result as? DigestGenerationResult.Failure)?.reason) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state.update { it.copy(failure = DigestFailure.STORAGE) }
            } finally { state.update { it.copy(generating = false) } }
        }
    }

    fun cancel() { generation?.cancel() }

    companion object {
        fun factory(repository: NewsDigestRepository, settings: DigestSettingsRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(DigestViewModel(repository, settings))!!
        }
    }
}
