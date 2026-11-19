package com.aster.core.data.repository

import com.aster.core.agent.runtime.*
import com.aster.core.agent.skills.*
import com.aster.core.analytics.NoOpLogger
import com.aster.core.localdb.dao.AgentConversationDao
import com.aster.core.localdb.entity.AgentConversationEntity
import com.aster.core.localdb.dto.AgentConversationSummaryRow
import com.aster.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DigestAgentRepositoryTest {
    private val refreshed = mutableListOf<String>()
    private val news = object : NewsRepository {
        override val sources = listOf(NewsSource("one", "来源一"), NewsSource("two", "来源二"))
        override fun observeCacheStats() = flowOf(NewsCacheStats(0, 0))
        override suspend fun clearCache() = Unit
        override fun observeNews() = flowOf(listOf(
            NewsArticle("one", "1", "科技新闻", "https://example.com/one", "已有摘要"),
            NewsArticle("two", "2", "不可访问的新闻", "https://example.com/two", "未选来源")
        ))
        override fun search(query: String, sourceId: String?) = observeNews()
        override suspend fun refresh(sourceId: String): NewsRefreshResult {
            refreshed += sourceId
            return NewsRefreshResult.Success
        }
    }
    private val settings = object : DigestSettingsRepository {
        override val settings = MutableStateFlow(DigestSettings(setOf("one"), hasApiKey = true))
        override suspend fun save(settings: DigestSettings, apiKey: String?) { this.settings.value = settings }
    }
    private var stored: AgentConversationEntity? = null
    private val dao = object : AgentConversationDao {
        override suspend fun read(id: Long) = stored?.takeIf { it.id == id }
        override suspend fun readLatest() = stored
        override suspend fun nextId() = (stored?.id ?: 0L) + 1
        override fun observeSummaries() = flowOf(emptyList<AgentConversationSummaryRow>())
        override suspend fun save(entity: AgentConversationEntity) { stored = entity }
    }
    private val skills = object : AgentSkillStore {
        override suspend fun list() = emptyList<AgentSkill>()
        override suspend fun read(name: String, path: String) = ""
    }

    @Test fun toolsOnlyReadSelectedSourcesAndReuseOneSnapshotPerRun() = runBlocking {
        val tools = NewsAgentTools(news, listOf(news.sources.first())).tools
        val search = tools.first().execute(buildJsonObject {})
        assertEquals(listOf("one"), refreshed)
        assertEquals(listOf("科技新闻"), search.references.map { it.title })
        assertFalse(search.content.contains("不可访问"))
        val read = tools.last().execute(buildJsonObject { put("ids", buildJsonArray { add("n1") }) })
        assertTrue(read.content.contains("已有摘要"))
        assertTrue(read.content.contains("不是网页全文"))
        assertEquals(1, refreshed.size)
        try { tools.last().execute(buildJsonObject { put("ids", buildJsonArray { add("n2") }) }); fail() }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun missingCredentialsNeverCallModelOrRefreshSources() = runBlocking {
        var calls = 0
        val client = AgentModelClient { _, _ -> calls++; emptyFlow() }
        val repo = DefaultDigestAgentRepository(news, settings, flowOf(""), dao, StreamingAgent(client, skills), NoOpLogger)
        assertEquals(listOf(AgentEvent.Failed(DigestFailure.NOT_CONFIGURED)), repo.reply("简报", emptyList()).toList())
        assertEquals(0, calls)
        assertTrue(refreshed.isEmpty())
    }

    @Test fun persistedInterruptedRunsRecoverAsCancelledAndDoNotPersistCredentials() = runBlocking {
        val repo = DefaultDigestAgentRepository(news, settings, flowOf("secret-key"), dao,
            StreamingAgent(AgentModelClient { _, _ -> emptyFlow() }, skills), NoOpLogger)
        repo.saveConversation(null, listOf(AgentMessage("one", AgentRole.ASSISTANT, "partial",
            steps = listOf(AgentStep("tool", "read_news", "读取", "{}")), status = AgentStatus.RUNNING)))
        val restored = repo.loadConversation()!!.messages.single()
        assertEquals(AgentStatus.CANCELLED, restored.status)
        assertEquals(AgentStatus.CANCELLED, restored.steps.single().status)
        assertEquals("partial", restored.text)
        assertFalse(stored!!.payload.contains("secret-key"))
    }

    @Test fun followupOmitsIncompletePairsAndIncludesTheNewQuestion() = runBlocking {
        var observed = emptyList<AgentModelMessage>()
        val client = AgentModelClient { request, _ ->
            observed = request.messages
            flowOf(AgentModelChunk.Text("回答"), AgentModelChunk.Finished("stop"))
        }
        val repo = DefaultDigestAgentRepository(news, settings, flowOf("key"), dao, StreamingAgent(client, skills), NoOpLogger)
        val history = listOf(
            AgentMessage("1", AgentRole.USER, "完整问题"), AgentMessage("2", AgentRole.ASSISTANT, "完整回答"),
            AgentMessage("3", AgentRole.USER, "中断问题"), AgentMessage("4", AgentRole.ASSISTANT, "半截回答", status = AgentStatus.CANCELLED)
        )
        repo.reply("继续", history).toList()
        assertEquals(listOf("完整问题", "完整回答", "继续"), observed.drop(1).map { it.content })
    }
}
