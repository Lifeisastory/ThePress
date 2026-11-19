package com.aster.core.data

import com.aster.core.analytics.LogEvent
import com.aster.core.analytics.LogLevel
import com.aster.core.analytics.Logger
import com.aster.core.model.HttpHeaders
import com.aster.core.model.NetworkError
import com.aster.core.model.ProcessingStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class NewsLogResponseTest {
    @Test
    fun httpDiagnosticsExcludeHeadersBodyAndMessage() {
        val events = mutableListOf<LogEvent>()
        Logger(events::add).logSourceFailure("baidu", NetworkError.Http(
            403, "private-message", HttpHeaders(listOf("Set-Cookie" to "private-cookie")), "private-body"
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
