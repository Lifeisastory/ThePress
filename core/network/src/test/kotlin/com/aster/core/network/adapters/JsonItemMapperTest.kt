package com.aster.core.network.adapters

import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.dto.SourceItem
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.JsonFieldEquals
import com.aster.core.network.model.ProcessingStage
import com.aster.core.network.model.UrlMapping
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonItemMapperTest {
    private val mapper = JsonItemMapper()
    private val baseUrl = "https://example.com/feed/list".toHttpUrl()
    private val rule = ItemMapping(
        itemsPath = JsonPath("items"),
        idPath = JsonPath("id"),
        titlePath = JsonPath("title"),
        url = UrlMapping.Field(JsonPath("url")),
        summaryPath = JsonPath("summary")
    )

    private fun map(text: String, mapping: ItemMapping = rule) =
        mapper.map("test", Json.parseToJsonElement(text), mapping, baseUrl)

    @Test
    fun optionalImagesResolveRelativeUrlsAndIgnoreMalformedValues() {
        val images = listOf(
            "\"/image.jpg\"" to "https://example.com/image.jpg",
            "\"//cdn.example.com/image.jpg\"" to "https://cdn.example.com/image.jpg",
            "\"http://i2.hdslb.com/a.jpg\"" to "https://i2.hdslb.com/a.jpg",
            "\"javascript:alert(1)\"" to null,
            "\"data:image/png;base64,a\"" to null,
            "\"\"" to null, "null" to null, "{}" to null, "42" to null
        )
        images.forEach { (image, expected) ->
            val result = map(
                """{"items":[{"id":"1","title":"标题","url":"/1","image":$image}]}""",
                rule.copy(imagePath = JsonPath("image"))
            )
            assertEquals(expected, result.single().imageUrl)
        }
        assertEquals(null, map(
            """{"items":[{"id":"1","title":"标题","url":"/1"}]}""",
            rule.copy(imagePath = JsonPath("image"))
        ).single().imageUrl)
    }

    @Test
    fun mapsNumericIdsRelativeLinksAndOptionalSummary() {
        val items = map("""{"items":[{"id":90071992547409931234,"title":" 标题 ","url":"article","summary":null}]}""")
        assertEquals(
            SourceItem("test", "90071992547409931234", "标题", "https://example.com/feed/article"),
            items.single()
        )
    }

    @Test
    fun templateEncodesIdAsUrlComponent() {
        val items = map(
            """{"items":[{"id":"a b/&?","title":"标题"}]}""",
            rule.copy(url = UrlMapping.Template("https://example.com/{id}.html"))
        )
        assertEquals("https://example.com/a%20b%2F%26%3F.html", items.single().url)
    }

    @Test
    fun exclusionsAndDeduplicationPreserveFirstOccurrenceOrder() {
        val items = map(
            """{"items":[
                {"pinned":true},
                {"id":"2","title":"第二条","url":"/2"},
                {"id":"1","title":"第一条","url":"/1"},
                {"id":"2","title":"重复","url":"/2"}
            ]}""",
            rule.copy(excludedItems = listOf(JsonFieldEquals(JsonPath("pinned"), JsonPrimitive(true))))
        )
        assertEquals(listOf("2", "1"), items.map { it.id })
        assertEquals("第二条", items.first().title)
    }

    @Test
    fun rootStatusRulesRejectBusinessErrors() {
        val checked = rule.copy(requiredRootValues = listOf(JsonFieldEquals(JsonPath("ret"), JsonPrimitive(0))))
        val error = assertThrows(AdapterException::class.java) { map("""{"ret":1,"items":[]}""", checked) }
        assertEquals(ProcessingStage.MAPPING, error.stage)
    }

    @Test
    fun brokenListOrRequiredFieldsDoNotBecomeEmptySuccess() {
        listOf(
            "{}", """{"items":{}}""", """{"items":[null]}""",
            """{"items":[{"id":"1","url":"/1"}]}""",
            """{"items":[{"id":"1","title":" ","url":"/1"}]}""",
            """{"items":[{"id":true,"title":"标题","url":"/1"}]}"""
        ).forEach { text ->
            val error = assertThrows(AdapterException::class.java) { map(text) }
            assertEquals(ProcessingStage.MAPPING, error.stage)
        }
    }

    @Test
    fun nonHttpLinksAreRejected() {
        listOf("javascript:alert(1)", "file:///tmp/a", "data:text/plain,example").forEach { url ->
            assertThrows(AdapterException::class.java) {
                map("""{"items":[{"id":"1","title":"标题","url":"$url"}]}""")
            }
        }
    }

    @Test
    fun emptyResultPolicyIsExplicit() {
        assertEquals(emptyList<SourceItem>(), map("""{"items":[]}"""))
        assertThrows(AdapterException::class.java) { map("""{"items":[]}""", rule.copy(allowEmpty = false)) }
    }
}
