package com.aster.core.data.repository

import com.aster.core.agent.*
import com.aster.core.analytics.*
import com.aster.core.localdb.dao.NewsDigestDao
import com.aster.core.localdb.entity.NewsDigestEntity
import com.aster.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class NewsDigestRepositoryTest {
    private val config = MutableStateFlow(DigestSettings(setOf("one"), hasApiKey = true))
    private val settings = object : DigestSettingsRepository {
        override val settings = config
        override suspend fun save(settings: DigestSettings, apiKey: String?) { config.value = settings }
    }
    private val articles = MutableStateFlow(listOf(
        NewsArticle("one", "1", "News", "https://example.com/1", "Summary"),
        NewsArticle("two", "2", "Unselected", "https://example.com/2")
    ))
    private val refreshes = mutableListOf<String>()
    private var refreshFailure = false
    private val news = object : NewsRepository {
        override val sources = listOf(NewsSource("one", "One"), NewsSource("two", "Two"))
        override fun observeNews() = articles
        override fun search(query: String, sourceId: String?) = articles
        override fun observeCacheStats() = flowOf(NewsCacheStats(0, 0))
        override suspend fun clearCache() { articles.value = emptyList() }
        override suspend fun refresh(sourceId: String): NewsRefreshResult {
            refreshes.add(sourceId)
            return if (refreshFailure) NewsRefreshResult.Failure(NewsFailureReason.CONNECTION) else NewsRefreshResult.Success
        }
    }
    private val persisted = MutableStateFlow<NewsDigestEntity?>(null)
    private var writeFailure = false
    private val dao = object : NewsDigestDao {
        override fun observeLatest() = persisted
        override suspend fun save(entity: NewsDigestEntity) {
            if (writeFailure) error("disk full")
            persisted.value = entity
        }
    }
    private val events = mutableListOf<LogEvent>()
    private val requests = mutableListOf<LlmRequest>()
    private fun result(ids: List<String>) = LlmResponse(buildJsonObject {
        put("items", buildJsonArray { add(buildJsonObject {
            put("title", "Digest"); put("body", "Combined facts")
            put("referenceIds", JsonArray(ids.map(::JsonPrimitive)))
        }) })
    }, buildJsonObject { put("total_tokens", 10) })
    private val client = LlmClient { request, key ->
        assertEquals("private-key", key)
        requests.add(request)
        val input = Json.parseToJsonElement(request.user).jsonArray
        val ids = input.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        result(ids)
    }
    private fun repository(client: LlmClient = this.client, key: String = "private-key") = DefaultNewsDigestRepository(
        news, settings, flowOf(key), dao, client, Logger { events.add(it) }, clock = { 123L }
    )

    @Test fun savesSelectedSourcesAndReferencesAndObservesWithoutCallingLlm() = runBlocking {
        val repository = repository()
        assertNull(repository.observeLatestDigest().first())
        assertTrue(requests.isEmpty())
        assertEquals(DigestGenerationResult.Success, repository.generateDigest())
        assertEquals(listOf("one"), refreshes)
        assertFalse(requests.single().user.contains("Unselected"))
        val digest = repository().observeLatestDigest().first()!!
        assertEquals(1, digest.inputCount)
        assertEquals("https://example.com/1", digest.items.single().references.single().url)
        assertEquals("Summary", digest.items.single().references.single().summary)
        news.clearCache()
        assertEquals(digest, repository.observeLatestDigest().first())
        assertTrue(events.any { it.attributes["origin"] == "NETWORK" && it.request != null })
        assertTrue(events.any { it.response?.jsonObject?.containsKey("usage") == true })
        assertFalse(events.toString().contains("private-key"))
    }

    @Test fun sendsAllArticlesInOneRequestAndKeepsEveryOriginalReference() = runBlocking {
        articles.value = (1..26).map { NewsArticle("one", "$it", "Title $it", "https://example.com/$it") }
        val repo = repository()
        assertEquals(DigestGenerationResult.Success, repo.generateDigest())
        assertEquals(1, requests.size)
        val input = Json.parseToJsonElement(requests.single().user).jsonArray
        assertEquals((1..26).map { "Title $it" }, input.map { it.jsonObject.getValue("title").jsonPrimitive.content })
        assertEquals((1..26).map { "n$it" }, repo.observeLatestDigest().first()!!.items.single().references.map { it.id })
    }

    @Test fun invalidReferenceOrWriteFailureKeepsPreviousDigest() = runBlocking {
        repository().generateDigest()
        val previous = persisted.value
        listOf(emptyList(), listOf("invented"), listOf("n1", "invented")).forEach { ids ->
            val repo = repository(LlmClient { _, _ -> result(ids) })
            assertEquals(DigestGenerationResult.Failure(DigestFailure.INVALID_RESPONSE), repo.generateDigest())
            assertEquals(previous, persisted.value)
        }
        writeFailure = true
        assertEquals(DigestGenerationResult.Failure(DigestFailure.STORAGE), repository().generateDigest())
        assertEquals(previous, persisted.value)
    }

    @Test fun missingReferencesArePersistedAsClearlyMarkedOriginalsWithoutAnotherRequest() = runBlocking {
        articles.value = listOf(
            NewsArticle("one", "1", "First", "https://example.com/1", "First summary"),
            NewsArticle("one", "2", "Second", "https://example.com/2", "Original summary"),
            NewsArticle("one", "3", "Third", "https://example.com/3")
        )
        var calls = 0
        val repo = repository(LlmClient { _, _ -> calls++; result(listOf("n1", "n1")) })
        assertEquals(DigestGenerationResult.Success, repo.generateDigest())
        assertEquals(1, calls)
        val digest = repository().observeLatestDigest().first()!!
        assertEquals(3, digest.inputCount)
        assertFalse(digest.items.first().isOriginal)
        assertEquals(listOf("n1"), digest.items.first().references.map { it.id })
        val originals = digest.items.filter { it.isOriginal }
        assertEquals(listOf("Second", "Third"), originals.map { it.title })
        assertEquals(listOf("Original summary", ""), originals.map { it.body })
        assertEquals(listOf("https://example.com/2", "https://example.com/3"), originals.flatMap { it.references }.map { it.url })
        val diagnostics = events.single { it.attributes["operation"] == "VALIDATE" && it.response != null }.response!!.jsonObject
        assertEquals("PARTIAL", diagnostics.getValue("outcome").jsonPrimitive.content)
        assertEquals(listOf("n2", "n3"), diagnostics.getValue("missingIds").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("n1"), diagnostics.getValue("duplicateIds").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun oneOriginalCanSupportMultipleEvents() = runBlocking {
        val singleItem = result(listOf("n1")).content.getValue("items").jsonArray.single()
        val repo = repository(LlmClient { _, _ -> LlmResponse(buildJsonObject {
            put("items", JsonArray(listOf(singleItem, singleItem)))
        }, buildJsonObject {}) })
        assertEquals(DigestGenerationResult.Success, repo.generateDigest())
        val items = repo.observeLatestDigest().first()!!.items
        assertEquals(2, items.size)
        assertTrue(items.all { !it.isOriginal && it.references.single().id == "n1" })
    }

    @Test fun reproducesLargeResponseWithMissingAndRepeatedReferences() = runBlocking {
        articles.value = (1..335).map { NewsArticle("one", "$it", "Title $it", "https://example.com/$it") }
        val content = javaClass.getResource("/digest-reference-regression.json")!!.readText()
        var calls = 0
        val repo = repository(LlmClient { _, _ ->
            calls++
            LlmResponse(Json.parseToJsonElement(content).jsonObject, buildJsonObject {})
        })
        assertEquals(DigestGenerationResult.Success, repo.generateDigest())
        assertEquals(1, calls)
        val items = repo.observeLatestDigest().first()!!.items
        assertEquals(135, items.count { !it.isOriginal })
        assertEquals(51, items.count { it.isOriginal })
        assertEquals((1..335).map { "n$it" }.toSet(), items.flatMap { it.references }.map { it.id }.toSet())
    }

    @Test fun malformedContentKeepsPreviousDigestAndLogsValidationReason() = runBlocking {
        repository().generateDigest()
        val previous = persisted.value
        val malformed = listOf("{}", "{\"items\":[]}",
            "{\"items\":[{\"title\":\"Title\",\"body\":\"\",\"referenceIds\":[\"n1\"]}]}")
        malformed.forEach { content ->
            val repo = repository(LlmClient { _, _ ->
                LlmResponse(Json.parseToJsonElement(content).jsonObject, buildJsonObject {})
            })
            assertEquals(DigestGenerationResult.Failure(DigestFailure.INVALID_RESPONSE), repo.generateDigest())
            assertEquals(previous, persisted.value)
            val response = events.last().response!!.jsonObject
            assertEquals("FAILED", response.getValue("outcome").jsonPrimitive.content)
            assertEquals("INVALID_STRUCTURE", response.getValue("reason").jsonPrimitive.content)
        }
        val legacy = """{"title":"Old","body":"Summary","references":[]}"""
        assertFalse(Json.decodeFromString<DigestItem>(legacy).isOriginal)
    }

    @Test fun reportsMissingConfigurationAndEmptyInputWithoutLlmCalls() = runBlocking {
        assertEquals(DigestGenerationResult.Failure(DigestFailure.NOT_CONFIGURED), repository(key = "").generateDigest())
        config.value = config.value.copy(sourceIds = emptySet())
        assertEquals(DigestGenerationResult.Failure(DigestFailure.NO_SOURCES), repository().generateDigest())
        config.value = config.value.copy(sourceIds = setOf("one"))
        articles.value = emptyList()
        assertEquals(DigestGenerationResult.Failure(DigestFailure.NO_NEWS), repository().generateDigest())
        assertTrue(requests.isEmpty())
    }

    @Test fun processesMoreThanThreeHundredArticlesWithoutDroppingReferences() = runBlocking {
        articles.value = (1..301).map { NewsArticle("one", "$it", "Title", "https://example.com/$it") }
        val repo = repository()
        assertEquals(DigestGenerationResult.Success, repo.generateDigest())
        val digest = repo.observeLatestDigest().first()!!
        assertEquals(301, digest.inputCount)
        assertEquals(1, requests.size)
        assertEquals(301, Json.parseToJsonElement(requests.single().user).jsonArray.size)
        assertEquals((1..301).map { "n$it" }, digest.items.flatMap { it.references }.map { it.id })
    }

    @Test fun rejectsOversizedTextBeforeCallingLlm() = runBlocking {
        articles.value = listOf(NewsArticle("one", "1", "Title", "https://example.com/1", "a".repeat(120_000)))
        assertEquals(DigestGenerationResult.Failure(DigestFailure.INPUT_TOO_LARGE), repository().generateDigest())
        assertTrue(requests.isEmpty())
    }

    @Test fun staleSourceIsRecordedAndHttpFailuresHaveSpecificReasons() = runBlocking {
        refreshFailure = true
        val repo = repository()
        repo.generateDigest()
        assertTrue(repo.observeLatestDigest().first()!!.sources.single().refreshFailed)
        mapOf(401 to DigestFailure.AUTHENTICATION, 402 to DigestFailure.BALANCE, 429 to DigestFailure.RATE_LIMIT, 503 to DigestFailure.SERVER)
            .forEach { (code, reason) ->
                assertEquals(DigestGenerationResult.Failure(reason),
                    repository(LlmClient { _, _ -> throw LlmException(LlmFailure.HTTP, code) }).generateDigest())
            }
    }

    @Test(timeout = 10_000) fun cancellationReleasesLockAndConcurrentCallDoesNotDuplicateCharges() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val repo = repository(LlmClient { _, _ -> entered.complete(Unit); awaitCancellation() })
        val first = launch { repo.generateDigest() }
        entered.await()
        assertEquals(DigestGenerationResult.Failure(DigestFailure.BUSY), repo.generateDigest())
        first.cancelAndJoin()
        assertNull(persisted.value)
        config.value = config.value.copy(sourceIds = emptySet())
        assertEquals(DigestGenerationResult.Failure(DigestFailure.NO_SOURCES), repo.generateDigest())
        assertTrue(events.any { it.response?.jsonObject?.get("outcome")?.jsonPrimitive?.content == "CANCELED" })
    }
}
