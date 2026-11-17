package com.aster.core.network

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.network.model.NetworkError
import com.aster.core.network.model.ProcessingStage
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class NetworkDiagnosticsTest {
    @Test
    fun httpDiagnosticsExcludeHeadersBodyAndMessage() {
        val events = mutableListOf<LogEvent>()
        Logger(events::add).logSourceFailure("baidu", NetworkError.Http(
            403, "private-message", Headers.headersOf("Set-Cookie", "private-cookie"), "private-body"
        ))
        val event = events.single()
        assertEquals("source_failed", event.name)
        assertEquals(LogLevel.ERROR, event.level)
        assertEquals("403", event.attributes["status"])
        assertEquals("baidu", event.attributes["sourceId"])
        assertFalse(event.toString().contains("private-"))
    }

    @Test
    fun processingDiagnosticsKeepTheStageAndCause() {
        val events = mutableListOf<LogEvent>()
        val cause = IllegalArgumentException("invalid field")
        Logger(events::add).logSourceFailure("baidu", NetworkError.Processing(
            "baidu", ProcessingStage.MAPPING, "invalid data", cause
        ))
        assertEquals("MAPPING", events.single().attributes["stage"])
        assertSame(cause, events.single().cause)
    }
}
