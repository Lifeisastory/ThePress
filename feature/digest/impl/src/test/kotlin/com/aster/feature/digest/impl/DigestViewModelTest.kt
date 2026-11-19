package com.aster.feature.digest.impl

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.aster.core.data.repository.DigestSettingsRepository
import com.aster.core.data.repository.NewsDigestRepository
import com.aster.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DigestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val snapshot = MutableStateFlow<NewsDigest?>(NewsDigest(1, "deepseek-flash", DigestDetail.STANDARD, emptyList(), 0, emptyList()))
    private val settings = object : DigestSettingsRepository {
        override val settings = MutableStateFlow(DigestSettings(setOf("one"), hasApiKey = true))
        override suspend fun save(settings: DigestSettings, apiKey: String?) { this.settings.value = settings }
    }
    private var calls = 0
    private fun create(generate: suspend () -> DigestGenerationResult): DigestViewModel {
        val repository = object : NewsDigestRepository {
            override fun observeLatestDigest() = snapshot
            override suspend fun generateDigest(): DigestGenerationResult { calls++; return generate() }
        }
        return ViewModelProvider(store, DigestViewModel.factory(repository, settings))[DigestViewModel::class.java]
    }
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }

    @Test fun observationAndSettingsChangesDoNotGenerateAndFailureKeepsPreviousResult() = runTest(dispatcher) {
        val vm = create { DigestGenerationResult.Failure(DigestFailure.RATE_LIMIT) }
        runCurrent()
        val previous = vm.uiState.value.digest
        settings.settings.value = settings.settings.value.copy(detail = DigestDetail.DETAILED)
        runCurrent()
        assertEquals(0, calls)
        vm.generate(); runCurrent()
        assertEquals(1, calls)
        assertEquals(DigestFailure.RATE_LIMIT, vm.uiState.value.failure)
        assertEquals(previous, vm.uiState.value.digest)
        assertFalse(vm.uiState.value.generating)
    }

    @Test fun repeatedClicksShareOneJobAndCancellationAllowsRetry() = runTest(dispatcher) {
        val vm = create { awaitCancellation() }
        runCurrent()
        vm.generate(); vm.generate(); runCurrent()
        assertEquals(1, calls)
        assertTrue(vm.uiState.value.generating)
        vm.cancel(); runCurrent()
        assertFalse(vm.uiState.value.generating)
        assertNull(vm.uiState.value.failure)
        assertNotNull(vm.uiState.value.digest)
        vm.generate(); runCurrent()
        assertEquals(2, calls)
        vm.cancel(); runCurrent()
    }
}
