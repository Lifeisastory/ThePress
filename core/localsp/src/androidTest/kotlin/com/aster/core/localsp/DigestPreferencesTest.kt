package com.aster.core.localsp

import androidx.test.platform.app.InstrumentationRegistry
import com.aster.core.model.DigestDetail
import com.aster.core.model.DigestSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DigestPreferencesTest {
    @Test fun settingsAndEncryptedKeySurviveRecreationAndSupportRemoval() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppSharedPreferences(context)
        val previous = preferences.digestSettings.first()
        val previousKey = preferences.digestApiKey.first()
        val secret = "test-deepseek-secret-123456"
        try {
            val settings = DigestSettings(setOf("baidu", "zhihu"), "deepseek-v4-pro", DigestDetail.DETAILED)
            preferences.saveDigestSettings(settings, secret)
            val recreated = AppSharedPreferences(context)
            assertEquals(settings.copy(hasApiKey = true), recreated.digestSettings.first())
            assertEquals(secret, recreated.digestApiKey.first())
            val bytes = File(context.filesDir, "datastore/user_preferences.preferences_pb").readBytes()
            assertFalse(String(bytes, Charsets.UTF_8).contains(secret))
            recreated.saveDigestSettings(settings.copy(sourceIds = setOf("baidu")), null)
            assertEquals(secret, preferences.digestApiKey.first())
            recreated.saveDigestSettings(settings, "")
            assertFalse(preferences.digestSettings.first().hasApiKey)
            assertEquals("", preferences.digestApiKey.first())
        } finally { preferences.saveDigestSettings(previous, previousKey) }
    }
}
