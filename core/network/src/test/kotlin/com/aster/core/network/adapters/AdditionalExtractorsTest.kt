package com.aster.core.network.adapters

import com.aster.core.network.adapters.extract.AssignedJsonExtractor
import com.aster.core.network.adapters.extract.FeedExtractor
import com.aster.core.network.adapters.extract.HtmlListExtractor
import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.HtmlField
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.ProcessingStage
import com.aster.core.network.model.UrlMapping
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdditionalExtractorsTest {
    private fun payload(body: String) = HttpPayload("https://example.com/feed/".toHttpUrl(), headersOf(), body)

    @Test
    fun assignedJsonHandlesMultilineNestedValuesAndBracesInsideStrings() {
        val body = """<script>
            var other = {};
            window.DATA = {"items":[{"title":"a }; b \\","nested":{"list":[]}}]}; window.after = 1;
        </script>"""
        val data = AssignedJsonExtractor("window.DATA").extract(payload(body), Json)
        assertEquals("a }; b \\", JsonPath("items[0].title").read(data)!!.jsonPrimitive.content)
    }

    @Test
    fun assignmentDoesNotEvaluateJavascriptOrAcceptTruncatedDocuments() {
        listOf("var DATA = fetch('/secret');", "var DATA = {items: []};", "var DATA = {\"items\": [", "var NOT_DATA = {};").forEach {
            val error = assertThrows(AdapterException::class.java) { AssignedJsonExtractor("DATA").extract(payload(it), Json) }
            assertEquals(ProcessingStage.EXTRACTION, error.stage)
        }
    }

    @Test
    fun htmlSelectorsDecodeEntitiesAndUseFirstMatchingChild() {
        val extractor = HtmlListExtractor("article", mapOf("title" to HtmlField("a"), "url" to HtmlField("a", "href")))
        val rows = extractor.extract(payload("<article><a href='/x?a=1&amp;b=2'>A &amp; <b>B</b></a><a>忽略</a></article>"), Json)
        assertEquals("A & B", rows.single().jsonObject.getValue("title").jsonPrimitive.content)
        assertEquals("/x?a=1&b=2", rows.single().jsonObject.getValue("url").jsonPrimitive.content)
        assertThrows(AdapterException::class.java) { extractor.extract(payload("<html>请验证</html>"), Json) }
    }

    @Test
    fun rssAndAtomUseStableIdsAndAlternateLinks() {
        val rss = FeedExtractor.extract(payload("""<rss><channel><item><title>A &amp; B</title><link>/article</link>
            <description><![CDATA[<b>说明</b> &amp; 详情]]></description></item></channel></rss>"""), Json)
        assertEquals("/article", rss.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("说明 & 详情", rss.single().jsonObject.getValue("summary").jsonPrimitive.content)
        val atom = FeedExtractor.extract(payload("""<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>urn:entry:1</id>
            <title>标题</title><link rel="self" href="/api/1"/><link rel="alternate" href="../article/1"/>
            <summary>摘要</summary></entry></feed>"""), Json)
        val rule = ItemMapping(JsonPath(""), JsonPath("id"), JsonPath("title"), UrlMapping.Field(JsonPath("url")))
        val result = JsonItemMapper().map("atom", atom, rule, payload("").url).single()
        assertEquals("urn:entry:1", result.id)
        assertEquals("https://example.com/article/1", result.url)
    }

    @Test
    fun feedRejectsChallengePagesAndExternalEntities() {
        listOf("<html>challenge</html>", "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]><rss><channel/></rss>").forEach {
            assertThrows(AdapterException::class.java) { FeedExtractor.extract(payload(it), Json) }
        }
        assertEquals(0, FeedExtractor.extract(payload("<rss><channel/></rss>"), Json).jsonArray.size)
    }
}
