package com.aster.feature.me.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.model.DigestSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class DigestSettingsUiState(
    val settings: DigestSettings? = null,
    val saving: Boolean = false,
    val readFailed: Boolean = false,
    val message: Int? = null,
    val savedRevision: Int = 0
)

internal class DigestSettingsViewModel(private val repository: DigestSettingsRepository) : ViewModel() {
    private val state = MutableStateFlow(DigestSettingsUiState())
    val uiState = state.asStateFlow()
    init {
        viewModelScope.launch {
            repository.settings.retryWhen { _, _ ->
                state.update { it.copy(readFailed = true) }; delay(3000); true
            }.collect { settings -> state.update { it.copy(settings = settings, readFailed = false) } }
        }
    }

    fun save(settings: DigestSettings, apiKey: String?) {
        if (state.value.saving) return
        state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                repository.save(settings, apiKey)
                state.update { it.copy(savedRevision = it.savedRevision + 1, message = R.string.profile_digest_saved) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { state.update { it.copy(message = R.string.profile_save_failed) }
            } finally { state.update { it.copy(saving = false) } }
        }
    }

    companion object {
        fun factory(repository: DigestSettingsRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = modelClass.cast(DigestSettingsViewModel(repository))!!
        }
    }
}
