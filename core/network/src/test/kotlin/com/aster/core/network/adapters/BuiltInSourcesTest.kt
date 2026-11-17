package com.aster.core.network.adapters

import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.SourceCredentials
import com.aster.core.network.sources.BuiltInSources
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers.Companion.headersOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInSourcesTest {
    @Test
    fun everyRegisteredSourceHasAnIndependentResponseContract() {
        val fixtures = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/adapters/sources.json")).readText()).jsonObject
        assertEquals(57, BuiltInSources.all.size)
        assertEquals(fixtures.keys, BuiltInSources.all.map { it.id }.toSet())
        assertEquals(BuiltInSources.all.size, BuiltInSources.all.distinctBy { it.id }.size)
        BuiltInSources.all.forEach { source ->
            val fixture = fixtures.getValue(source.id).jsonObject
            try {
                val documents = fixture.getValue("bodies").jsonArray.map {
                    source.extractor.extract(HttpPayload(source.request.url, headersOf(), it.jsonPrimitive.content), Json)
                }
                val items = JsonItemMapper().map(source.id, source.transform(documents), source.mapping, source.request.url)
                val first = items.first()
                assertEquals(source.id, first.sourceId)
                assertEquals(fixture.getValue("id").jsonPrimitive.content, first.id)
                assertEquals(fixture.getValue("title").jsonPrimitive.content, first.title)
                assertEquals(fixture.getValue("url").jsonPrimitive.content, first.url)
                fixture["summary"]?.let { assertEquals(it.jsonPrimitive.content, first.summary) }
            } catch (error: Throwable) {
                throw AssertionError("来源契约失败：${source.id}", error)
            }
        }
    }

    @Test
    fun credentialsAreOptInAndDoNotMutateDefaultDefinitions() {
        val configured = BuiltInSources.create(SourceCredentials("test-token", "SUB=test-session")).associateBy { it.id }
        val defaults = BuiltInSources.all.associateBy { it.id }
        assertEquals("Bearer test-token", configured.getValue("producthunt").request.headers["Authorization"])
        assertEquals("SUB=test-session", configured.getValue("weibo").request.headers["Cookie"])
        assertFalse(defaults.getValue("producthunt").request.headers.containsKey("Authorization"))
        assertFalse(defaults.getValue("weibo").request.headers.containsKey("Cookie"))
        val product = configured.getValue("producthunt")
        val request = product.prepareRequest(product.request)
        val body = request.jsonBody as JsonObject
        assertTrue(body.getValue("query").jsonPrimitive.content.contains("\$postedAfter"))
        assertNotNull(body.getValue("variables").jsonObject["postedAfter"])
        assertNotNull(product.fallback)
    }

    @Test
    fun requestPreparationGeneratesFreshTokensAndSignedQueries() {
        val sources = BuiltInSources.all.associateBy { it.id }
        val coolapk = sources.getValue("coolapk")
        val first = coolapk.prepareRequest(coolapk.request)
        val second = coolapk.prepareRequest(coolapk.request)
        assertNotEquals(first.headers["X-App-Token"], second.headers["X-App-Token"])
        assertTrue(checkNotNull(first.headers["X-App-Token"]).matches(Regex("[0-9a-f]{32}[0-9a-f-]{36}0x[0-9a-f]+")))
        val cls = sources.getValue("cls-telegraph")
        val signed = cls.prepareRequest(cls.request).query
        assertEquals("30", signed["rn"])
        assertTrue(checkNotNull(signed["sign"]).matches(Regex("[0-9a-f]{32}")))
        assertTrue(checkNotNull(signed["last_time"]).toLong() > 0)
    }
}
