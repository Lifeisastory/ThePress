package com.aster.feature.me.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.model.AppearanceSettings
import com.aster.core.model.NewsCacheStats
import com.aster.core.model.ThemeColor
import com.aster.core.model.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProfileUiState(
    val appearance: AppearanceSettings? = null,
    val cache: NewsCacheStats? = null,
    val appearanceReadFailed: Boolean = false,
    val cacheReadFailed: Boolean = false,
    val saving: Boolean = false,
    val clearing: Boolean = false,
    val message: Int? = null
)

internal class ProfileViewModel(
    private val repository: NewsRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {
    private val state = MutableStateFlow(ProfileUiState())
    val uiState = state.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.appearance.retryWhen { _, _ ->
                state.update { it.copy(appearanceReadFailed = true) }
                delay(3000)
                true
            }.collect { appearance ->
                state.update { it.copy(appearance = appearance, appearanceReadFailed = false) }
            }
        }
        viewModelScope.launch {
            repository.observeCacheStats().retryWhen { _, _ ->
                state.update { it.copy(cacheReadFailed = true) }
                delay(3000)
                true
            }.collect { cache -> state.update { it.copy(cache = cache, cacheReadFailed = false) } }
        }
    }

    fun setThemeMode(mode: ThemeMode) = save { preferences.saveThemeMode(mode) }

    fun setThemeColor(color: ThemeColor) = save { preferences.saveThemeColor(color) }

    private fun save(block: suspend () -> Unit) {
        if (state.value.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(message = R.string.profile_save_failed) }
            } finally {
                state.update { it.copy(saving = false) }
            }
        }
    }

    fun clearCache() {
        if (state.value.clearing) return
        state.update { it.copy(clearing = true) }
        viewModelScope.launch {
            try {
                repository.clearCache()
                state.update { it.copy(message = R.string.profile_cache_cleared) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                state.update { it.copy(message = R.string.profile_cache_clear_failed) }
            } finally {
                state.update { it.copy(clearing = false) }
            }
        }
    }

    fun dismissMessage() { state.update { it.copy(message = null) } }

    companion object {
        fun factory(repository: NewsRepository, preferences: UserPreferencesRepository) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass == ProfileViewModel::class.java)
                    return modelClass.cast(ProfileViewModel(repository, preferences))!!
                }
            }
    }
}
