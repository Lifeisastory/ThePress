package com.aster.core.network.adapters

import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.dto.SourceStockInfo
import com.aster.core.network.sources.BuiltInSources
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XueqiuStockMappingTest {
    private val source = BuiltInSources.all.single { it.id == "xueqiu-hotstock" }

    private fun map(fields: String) = JsonItemMapper().map(
        source.id,
        Json.parseToJsonElement("""{"data":{"items":[{"ad":1},{"code":"00700","name":"腾讯控股","ad":0,$fields}]}}"""),
        source.mapping,
        source.request.url
    ).single()

    @Test
    fun mapsQuotePreservingLeadingZerosAndPercentageUnits() {
        val article = map(""""current":372.8,"chg":-3.2,"percent":-0.85""")
        assertEquals(SourceStockInfo("00700", 372.8, -3.2, -0.85), article.stockInfo)
        assertEquals("https://xueqiu.com/s/00700", article.url)
        assertEquals("腾讯控股", article.title)
    }

    @Test
    fun supportsPositiveZeroAndNumericStringQuotes() {
        assertEquals(SourceStockInfo("00700", 160.19, 4.73, 3.042583),
            map(""""current":"160.19","chg":"4.73","percent":"3.042583" """).stockInfo)
        assertEquals(SourceStockInfo("00700", 10.0, 0.0, 0.0),
            map(""""current":10,"chg":0,"percent":0""").stockInfo)
    }

    @Test
    fun invalidOrIncompleteQuoteKeepsArticleWithoutInventingZeroValues() {
        listOf("null", "{}", "true", "\"NaN\"", "\"Infinity\"", "\"invalid\"", "-1").forEach { value ->
            val article = map(""""current":$value,"chg":-3.2,"percent":-0.85""")
            assertNull(article.stockInfo)
            assertEquals("00700", article.id)
        }
        assertNull(map(""""current":372.8,"chg":-3.2""").stockInfo)
        assertNull(map(""""current":372.8,"percent":-0.85""").stockInfo)
        assertNull(map(""""unused":null""").stockInfo)
    }
}
