package com.aster.core.data.repository

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.Logger
import com.aster.core.model.DigestSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DigestSettingsRepositoryTest {
    @Test fun logsSettingsWithoutLoggingCredentialsAndSupportsPreserveReplaceRemove() = runBlocking {
        val state = MutableStateFlow(DigestSettings())
        val events = mutableListOf<LogEvent>()
        val keys = mutableListOf<String?>()
        val repository = DefaultDigestSettingsRepository(state, { settings, key ->
            keys.add(key); state.value = settings.copy(hasApiKey = key?.isNotBlank() ?: state.value.hasApiKey)
        }, Logger { events.add(it) })
        val settings = DigestSettings(sourceIds = setOf("baidu"))
        repository.save(settings, "private-secret-key")
        assertTrue(repository.settings.first().hasApiKey)
        repository.save(settings, null)
        repository.save(settings, "")
        assertFalse(repository.settings.first().hasApiKey)
        assertEquals(listOf("private-secret-key", null, ""), keys)
        assertFalse(events.toString().contains("private-secret-key"))
        assertTrue(events.filter { it.name == "request_started" }.all { it.request != null })
        assertTrue(events.filter { it.name == "request_finished" }.all { it.response != null })
    }
}
