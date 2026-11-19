package com.aster.core.data.repository

import com.aster.core.agent.runtime.AgentTool
import com.aster.core.agent.runtime.AgentToolResult
import com.aster.core.model.DigestReference
import com.aster.core.model.NewsRefreshResult
import com.aster.core.model.NewsSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

/** 每轮固定一份资料快照，工具只能访问用户选中的来源 */
internal class NewsAgentTools(private val news: NewsRepository, private val sources: List<NewsSource>) {
    private var references: List<DigestReference>? = null
    private var staleSources: List<String> = emptyList()

    val tools: List<AgentTool> = listOf(
        AgentTool("search_news", "查询资讯", "刷新所选来源并检索标题与摘要，query 可留空，offset 可继续翻页",
            schema(buildJsonObject {
                put("query", field("string", "关键词，留空浏览最新资讯"))
                put("limit", field("integer", "每页条数，1 到 30，默认 15"))
                put("offset", field("integer", "跳过条数，默认 0"))
            })
        ) { args ->
            val query = args["query"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            require(query.length <= 100)
            val limit = args["limit"]?.jsonPrimitive?.int ?: 15
            val offset = args["offset"]?.jsonPrimitive?.int ?: 0
            require(limit in 1..30 && offset >= 0)
            val found = snapshot().filter { query.isEmpty() || it.title.contains(query, true) || it.summary.orEmpty().contains(query, true) }
            val page = found.drop(offset).take(limit)
            AgentToolResult(buildJsonObject {
                put("total", found.size); put("offset", offset); put("returned", page.size)
                put("hasMore", offset.toLong() + page.size < found.size)
                put("staleSources", JsonArray(staleSources.map(::JsonPrimitive)))
                put("scope", "仅包含用户所选来源；刷新失败的来源使用已有缓存；不是全网搜索")
                put("articles", JsonArray(page.map { referenceJson(it, includeSummary = false) }))
            }.toString(), page)
        },
        AgentTool("read_news", "读取新闻材料", "按 search_news 返回的编号读取缓存标题和摘要，不会访问网页全文",
            schema(buildJsonObject {
                put("ids", buildJsonObject { put("type", "array"); put("items", field("string", "新闻编号")) })
            }, "ids")
        ) { args ->
            val ids = args.getValue("ids").jsonArray.map { it.jsonPrimitive.content }.distinct()
            require(ids.isNotEmpty() && ids.size <= 10)
            val all = snapshot().associateBy { it.id }
            require(ids.all { it in all })
            val items = ids.map(all::getValue)
            AgentToolResult(buildJsonObject {
                put("scope", "以下为应用缓存的标题与摘要，不是网页全文；摘要最多保留 1500 字符")
                put("articles", JsonArray(items.map { referenceJson(it, includeSummary = true) }))
            }.toString(), items)
        }
    )

    private suspend fun snapshot(): List<DigestReference> {
        references?.let { return it }
        val semaphore = Semaphore(4)
        val refreshes = coroutineScope {
            sources.map { source -> async { semaphore.withPermit { source to news.refresh(source.id) } } }.awaitAll()
        }
        staleSources = refreshes.filter { it.second is NewsRefreshResult.Failure }.map { it.first.name }
        val names = sources.associate { it.id to it.name }
        return news.observeNews().first().filter { it.sourceId in names }
            .distinctBy { it.sourceId to it.id }.mapIndexed { index, article ->
                DigestReference("n${index + 1}", article.sourceId, names.getValue(article.sourceId), article.id,
                    article.title.take(500), article.summary?.take(1500), article.url)
            }.also { references = it }
    }

    private fun referenceJson(reference: DigestReference, includeSummary: Boolean) = buildJsonObject {
        put("id", reference.id); put("source", reference.sourceName); put("title", reference.title)
        if (includeSummary) put("summary", reference.summary?.let(::JsonPrimitive) ?: JsonNull)
    }

    private fun field(type: String, description: String) = buildJsonObject { put("type", type); put("description", description) }

    private fun schema(properties: JsonObject, vararg required: String) = buildJsonObject {
        put("type", "object"); put("properties", properties)
        put("required", JsonArray(required.map(::JsonPrimitive))); put("additionalProperties", false)
    }
}
