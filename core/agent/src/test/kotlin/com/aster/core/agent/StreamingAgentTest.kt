package com.aster.core.agent

import com.aster.core.agent.model.DigestGenerationException
import com.aster.core.agent.runtime.*
import com.aster.core.agent.skills.*
import com.aster.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StreamingAgentTest {
    private val skills = object : AgentSkillStore {
        override suspend fun list() = listOf(AgentSkill("daily-news", "description"))
        override suspend fun read(name: String, path: String) = "instructions"
    }

    @Test fun badToolArgumentsAreReturnedToModelAndCanBeCorrected() = runBlocking {
        var step = 0
        val client = AgentModelClient { request, _ -> flow {
            if (step++ == 0) {
                emit(AgentModelChunk.Tool(0, "one", "use_skill", "not json"))
                emit(AgentModelChunk.Finished("tool_calls"))
            } else {
                assertTrue(request.messages.last().content.contains("失败"))
                emit(AgentModelChunk.Text("请调整问题"))
                emit(AgentModelChunk.Finished("stop"))
            }
        } }
        val events = StreamingAgent(client, skills).run("model", "system", emptyList(), emptyList(), "key").toList()
        assertFalse(events.filterIsInstance<AgentEvent.StepFinished>().single().success)
        assertEquals(AgentEvent.Completed, events.last())
    }

    @Test fun runawayToolLoopStopsAtTheConfiguredLimit() = runBlocking {
        var calls = 0
        val client = AgentModelClient { _, _ -> flow {
            emit(AgentModelChunk.Tool(0, "call-${calls++}", "use_skill", "{\"name\":\"daily-news\"}"))
            emit(AgentModelChunk.Finished("tool_calls"))
        } }
        try { StreamingAgent(client, skills, maxSteps = 2).run("model", "system", emptyList(), emptyList(), "key").toList(); fail() }
        catch (error: DigestGenerationException) { assertEquals(DigestFailure.STEP_LIMIT, error.reason) }
        assertEquals(2, calls)
    }

    @Test fun cancellationInsideAToolIsNotConvertedIntoAToolFailure() = runBlocking {
        val client = AgentModelClient { _, _ -> flowOf(
            AgentModelChunk.Tool(0, "one", "cancel", "{}"), AgentModelChunk.Finished("tool_calls")) }
        val tool = AgentTool("cancel", "cancel", "cancel", buildJsonObject {}) { throw CancellationException("cancel") }
        val events = mutableListOf<AgentEvent>()
        try { StreamingAgent(client, skills).run("model", "system", emptyList(), listOf(tool), "key").toList(events); fail() }
        catch (_: CancellationException) { assertTrue(events.none { it is AgentEvent.StepFinished || it is AgentEvent.Completed }) }
    }

    @Test fun skillMetadataAndResourcePathsStayInsideTheSkill() {
        assertEquals(AgentSkill("daily-news", "Daily briefing"), parseSkillMetadata("---\nname: daily-news\ndescription: Daily briefing\n---\n# body"))
        assertTrue(isSkillResourcePath("references/style.md"))
        listOf("../secret.md", "/secret.md", "references/../../secret.md", "C:/secret.md", "ref\\secret.md", "script.py").forEach {
            assertFalse(it, isSkillResourcePath(it))
        }
    }
}
