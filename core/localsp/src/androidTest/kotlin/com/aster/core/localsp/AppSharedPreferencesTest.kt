package com.aster.core.localsp

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AppSharedPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = AppSharedPreferences(context)
    private var previousOrder = emptyList<String>()

    @Before
    fun setUp() = runBlocking {
        previousOrder = preferences.sourceOrder.first()
        preferences.saveSourceOrder(emptyList())
    }

    @After
    fun tearDown() = runBlocking { preferences.saveSourceOrder(previousOrder) }

    @Test
    fun sourceOrderPreservesOrderAndRemovesBlankAndDuplicateIds() = runBlocking {
        preferences.saveSourceOrder(listOf("two", "", "one", "two", " "))
        assertEquals(listOf("two", "one"), preferences.sourceOrder.first())
        assertEquals(listOf("two", "one"), AppSharedPreferences(context).sourceOrder.first())
    }

    @Test
    fun existingObserverReceivesWritesFromAnotherWrapper() = runBlocking {
        val initial = CompletableDeferred<Unit>()
        val updates = async {
            withTimeout(5_000) {
                preferences.sourceOrder.onEach { initial.complete(Unit) }.take(2).toList()
            }
        }
        initial.await()
        AppSharedPreferences(context).saveSourceOrder(listOf("two", "one"))
        assertEquals(listOf(emptyList<String>(), listOf("two", "one")), updates.await())
    }
}
