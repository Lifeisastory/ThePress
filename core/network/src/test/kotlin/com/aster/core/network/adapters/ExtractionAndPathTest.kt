package com.aster.core.network.adapters

import com.aster.core.network.adapters.extract.EmbeddedJsonExtractor
import com.aster.core.network.adapters.extract.JsonExtractor
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.dto.HttpPayload
import com.aster.core.model.ProcessingStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ExtractionAndPathTest {
    private fun payload(text: String) = HttpPayload("https://example.com/".toHttpUrl(), headersOf(), text)

    @Test
    fun pathsSupportFieldsAndNestedArrayIndices() {
        val root = Json.parseToJsonElement("""{"data":{"cards":[[{"title":"新闻"}]]}}""")
        assertEquals(JsonPrimitive("新闻"), JsonPath("data.cards[0][0].title").read(root))
        assertSame(root, JsonPath("").read(root))
        assertEquals(JsonPrimitive(1), JsonPath("[0]").read(Json.parseToJsonElement("[1]")))
    }

    @Test
    fun absentPathsAreDifferentFromExplicitNull() {
        val root = Json.parseToJsonElement("""{"items":[],"value":null}""")
        assertNull(JsonPath("items[1]").read(root))
        assertNull(JsonPath("items.title").read(root))
        assertNull(JsonPath("missing").read(root))
        assertEquals(JsonNull, JsonPath("value").read(root))
    }

    @Test
    fun invalidPathSyntaxFailsWhenConfigured() {
        listOf(".data", "data.", "data..id", "data[-1]", "data[]", "data.[0]", "a[0]id", "a[999999999999]")
            .forEach { path -> assertThrows(IllegalArgumentException::class.java) { JsonPath(path) } }
    }

    @Test
    fun jsonExtractorPreservesLargeNumericIds() {
        val document = JsonExtractor.extract(payload("""{"id":90071992547409931234}"""), Json)
        assertEquals("90071992547409931234", (JsonPath("id").read(document) as JsonPrimitive).content)
    }

    @Test
    fun embeddedExtractorUsesLiteralMarkersAndSupportsNewlines() {
        val extractor = EmbeddedJsonExtractor("<!--data[0]:", "-->")
        val document = extractor.extract(payload("<html><!--data[0]:\n{\"value\":\"内容\"}\n--></html>"), Json)
        assertEquals(JsonPrimitive("内容"), JsonPath("value").read(document))
    }

    @Test
    fun missingMarkersAndInvalidJsonProduceExtractionErrors() {
        val extractor = EmbeddedJsonExtractor("<!--s-data:", "-->")
        listOf("<html>验证页面</html>", "<!--s-data:{}", "<!--s-data:not-json-->").forEach { text ->
            val error = assertThrows(AdapterException::class.java) { extractor.extract(payload(text), Json) }
            assertEquals(ProcessingStage.EXTRACTION, error.stage)
        }
        val error = assertThrows(AdapterException::class.java) { JsonExtractor.extract(payload("<html/>"), Json) }
        assertEquals(ProcessingStage.EXTRACTION, error.stage)
    }
}
