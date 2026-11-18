package com.aster.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StockNumberFormatTest {
    @Test
    fun preservesQuotePrecisionAndAddsOnlyPositiveSigns() {
        assertEquals("372.80", formatStockNumber(372.8))
        assertEquals("-0.4355", formatStockNumber(-0.4355, signed = true))
        assertEquals("+3.042583", formatStockNumber(3.042583, signed = true))
        assertEquals("0.00", formatStockNumber(-0.0, signed = true))
        assertEquals("0.000001", formatStockNumber(0.000001))
        assertEquals("100000000.00", formatStockNumber(100000000.0))
    }
}
