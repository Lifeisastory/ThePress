package com.aster.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpHeadersTest {
    @Test
    fun lookupIgnoresCaseAndPreservesRepeatedValuesInOrder() {
        val headers = HttpHeaders(listOf(
            "Set-Cookie" to "first=1",
            "Retry-After" to "60",
            "set-cookie" to "second=2"
        ))

        assertEquals("60", headers["retry-after"])
        assertEquals("second=2", headers["SET-COOKIE"])
        assertEquals(listOf("first=1", "second=2"), headers.values("Set-Cookie"))
    }

    @Test
    fun missingHeaderHasNoSingleOrMultipleValues() {
        val headers = HttpHeaders()

        assertNull(headers["Retry-After"])
        assertEquals(emptyList<String>(), headers.values("Retry-After"))
    }
}
