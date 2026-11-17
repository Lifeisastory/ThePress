package com.aster.core.network.adapters

import com.aster.core.network.adapters.mapping.JsonItemMapper
import com.aster.core.network.sources.BuiltInSources
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltInImageMappingTest {
    @Test
    fun sourceMappingsPreserveVerifiedCoverFields() {
        val samples = mapOf(
            "baidu" to """{"data":{"cards":[{"content":[{"rawUrl":"https://example.com/1","word":"标题","img":"https://example.com/cover.jpg"}]}]}}""",
            "bilibili-hot-video" to """{"data":{"list":[{"bvid":"1","title":"标题","pic":"https://example.com/cover.jpg"}]}}""",
            "bilibili-ranking" to """{"data":{"list":[{"bvid":"1","title":"标题","pic":"https://example.com/cover.jpg"}]}}""",
            "qqvideo-tv-hotsearch" to """{"ret":0,"data":{"card":{"children_list":{"list":{"cards":[{"id":"1","params":{"title":"标题","image_url":"https://example.com/cover.jpg"}}]}}}}}""",
            "toutiao" to """{"data":[{"ClusterIdStr":"1","Title":"标题","Image":{"url":"https://example.com/cover.jpg"}}]}"""
        )
        samples.forEach { (id, body) ->
            val source = BuiltInSources.all.single { it.id == id }
            val result = JsonItemMapper().map(id, Json.parseToJsonElement(body), source.mapping, source.request.url)
            assertEquals(id, "https://example.com/cover.jpg", result.single().imageUrl)
        }
    }

    @Test
    fun zhihuTransformKeepsImageAndAcceptsAbsentImage() {
        val source = BuiltInSources.all.single { it.id == "zhihu" }
        val document = Json.parseToJsonElement("""
            {"data":[{"target":{"link":{"url":"https://www.zhihu.com/question/1"},"title_area":{"text":"标题"},"image_area":{"url":"https://example.com/cover.jpg"}}}]}
        """.trimIndent())
        val transformed = source.transform(listOf(document))
        val result = JsonItemMapper().map(source.id, transformed, source.mapping, source.request.url)
        assertEquals("https://example.com/cover.jpg", result.single().imageUrl)
    }
}
