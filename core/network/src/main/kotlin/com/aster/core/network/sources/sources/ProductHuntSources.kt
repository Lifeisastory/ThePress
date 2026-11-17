package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.model.HttpMethod
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.sources.SourceDefaults
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal object ProductHuntSources {
    private val feed = SourceDefaults.feed("producthunt", "Product Hunt", "https://www.producthunt.com/feed")

    fun posts(token: String?): SourceDefinition {
        if (token.isNullOrBlank()) return feed
        return SourceDefaults.json(
            "producthunt", "Product Hunt", "https://api.producthunt.com/v2/api/graphql",
            SourceDefaults.mapping(summary = "summary"), headers = mapOf("Authorization" to "Bearer $token", "Accept" to "application/json")
        ).copy(
            prepareRequest = {
                val midnight = Calendar.getInstance(TimeZone.getTimeZone("America/Los_Angeles")).apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(midnight)
                it.copy(method = HttpMethod.POST, jsonBody = buildJsonObject {
                    put("query", "query(\$postedAfter: DateTime!) { posts(first: 30, order: RANKING, postedAfter: \$postedAfter) { edges { node { id name tagline url slug } } } }")
                    putJsonObject("variables") { put("postedAfter", date) }
                })
            },
            transform = { documents ->
                val root = documents.single() as? JsonObject ?: JsonDocuments.fail("GraphQL 响应不是对象")
                if ((root["errors"] as? JsonArray)?.isNotEmpty() == true) JsonDocuments.fail("GraphQL 返回错误")
                JsonArray(JsonDocuments.array(root, "data.posts.edges").map {
                    buildJsonObject {
                        put("id", JsonDocuments.requireText(it, "node.id"))
                        put("title", JsonDocuments.requireText(it, "node.name"))
                        put("url", JsonDocuments.text(it, "node.url") ?: "https://www.producthunt.com/posts/${JsonDocuments.requireText(it, "node.slug")}")
                        JsonDocuments.text(it, "node.tagline")?.let { summary -> put("summary", summary) }
                    }
                })
            }, fallback = feed
        )
    }
}
