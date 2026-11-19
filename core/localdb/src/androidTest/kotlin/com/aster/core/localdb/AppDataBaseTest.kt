package com.aster.core.localdb

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aster.core.localdb.dao.NewsCacheDao
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.CachedStockInfo
import com.aster.core.localdb.entity.NewsDigestEntity
import com.aster.core.localdb.entity.AgentConversationEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.entity.NewsCacheEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDataBaseTest {
    @get:Rule
    val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDataBase::class.java)
    private lateinit var database: AppDataBase
    private lateinit var dao: NewsCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDataBase::class.java
        ).build()
        dao = database.newsCacheDao()
    }

    @After
    fun tearDown() { database.close() }

    private fun snapshot(sourceId: String, time: Long, vararg ids: String) = NewsCache(
        NewsCacheEntity(sourceId, time),
        ids.mapIndexed { position, id ->
            CachedNewsArticleEntity(
                sourceId,
                position,
                id,
                "标题 $id",
                "https://example.com/$id",
                if (position == 0) null else "摘要"
            )
        }
    )

    @Test
    fun migratesVersionFiveKeepingOldChatAndStoresIndependentHistory() = runBlocking {
        val name = "agent-history-migration-test.db"
        migrations.createDatabase(name, 5).apply {
            execSQL("INSERT INTO agent_conversation (id, payload) VALUES (1, '[{\"text\":\"legacy\"}]')")
            close()
        }
        migrations.runMigrationsAndValidate(name, 6, true).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            val db = Room.databaseBuilder(context, AppDataBase::class.java, name).build()
            try {
                val conversations = db.agentConversationDao()
                assertEquals("[{\"text\":\"legacy\"}]", conversations.read(1)!!.payload)
                assertEquals(0L, conversations.read(1)!!.updatedAt)
                val second = conversations.saveNew("[{}]", "second", 20, 2)
                val third = conversations.saveNew("[{\"text\":\"third\"}]", "third", 30, 2)
                assertEquals(listOf(third, second, 1L), conversations.observeSummaries().first().map { it.id })
                assertEquals(third, conversations.readLatest()!!.id)
                assertEquals("[{}]", conversations.read(second)!!.payload)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun migratesVersionFourAndPersistsConversationWithoutDeletingDigest() = runBlocking {
        val name = "agent-conversation-migration-test.db"
        migrations.createDatabase(name, 4).apply {
            execSQL("INSERT INTO news_digest (id, payload) VALUES (1, 'old-digest')")
            close()
        }
        migrations.runMigrationsAndValidate(name, 5, true).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = AgentConversationEntity(payload = "[{\"text\":\"hello\"}]")
        try {
            val db = Room.databaseBuilder(context, AppDataBase::class.java, name).build()
            try {
                assertEquals("old-digest", db.newsDigestDao().observeLatest().first()!!.payload)
                db.agentConversationDao().save(saved)
                db.newsCacheDao().clearCache()
                assertEquals(saved, db.agentConversationDao().read())
            } finally { db.close() }
            val reopened = Room.databaseBuilder(context, AppDataBase::class.java, name).build()
            try { assertEquals(saved, reopened.agentConversationDao().read()) } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun searchesChineseSummaryAndLiteralWildcardsWithinSource() = runBlocking {
        val first = snapshot("one", 10, "a", "b").let {
            it.copy(articles = listOf(
                it.articles[0].copy(title = "中文标题 50%_\\", imageUrl = "https://example.com/a.jpg"),
                it.articles[1].copy(title = "Other", summary = "中文摘要")
            ))
        }
        dao.replace(first)
        dao.replace(snapshot("two", 20, "c").let { it.copy(articles = it.articles.map { row -> row.copy(title = "中文") }) })
        assertEquals(3, dao.search(null, "%中文%").first().size)
        assertEquals(listOf("a", "b"), dao.search("one", "%中文%").first().map { it.id })
        assertEquals("b", dao.search("one", "%摘要%").first().single().id)
        assertEquals("a", dao.search(null, "%50\\%\\_\\\\%").first().single().id)
        assertEquals(emptyList<CachedNewsArticleEntity>(), dao.search("two", "%摘要%").first())
        assertEquals("https://example.com/a.jpg", dao.read("one")!!.articles.first().imageUrl)
        dao.replace(snapshot("one", 30, "new"))
        assertEquals(emptyList<CachedNewsArticleEntity>(), dao.search("one", "%中文%").first())
    }

    @Test
    fun observedSearchUpdatesAfterSnapshotReplacement() = runBlocking {
        dao.replace(snapshot("one", 10, "old"))
        val initial = CompletableDeferred<Unit>()
        val updates = async {
            withTimeout(5_000) {
                dao.search("one", "%标题%")
                    .onEach { initial.complete(Unit) }
                    .take(2).toList()
            }
        }
        initial.await()
        dao.replace(snapshot("one", 20, "new"))
        assertEquals(listOf("old", "new"), updates.await().map { it.single().id })
    }

    @Test
    fun observedArticlesReplaceWholeSnapshotsAndKeepOtherSources() = runBlocking {
        dao.replace(snapshot("one", 10, "old"))
        dao.replace(snapshot("two", 10, "other"))
        val initial = CompletableDeferred<Unit>()
        val updates = async {
            withTimeout(5_000) {
                dao.observeArticles().onEach { initial.complete(Unit) }.take(2).toList()
            }
        }
        initial.await()
        dao.replace(snapshot("one", 20, "new", "next"))
        assertEquals(listOf(listOf("old", "other"), listOf("new", "next", "other")),
            updates.await().map { rows -> rows.map { it.id } })
        dao.replace(snapshot("one", 30))
        assertEquals(listOf("other"), dao.observeArticles().first().map { it.id })
    }

    @Test
    fun migratesVersionOneWithoutLosingCachedArticles() {
        val name = "news-migration-test.db"
        migrations.createDatabase(name, 1).apply {
            execSQL("INSERT INTO news_cache (sourceId, fetchedAt) VALUES ('one', 123)")
            execSQL("INSERT INTO cached_news_articles (sourceId, position, id, title, url, summary) VALUES ('one', 0, 'old', '旧内容', 'https://example.com/old', NULL)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 2, true).apply {
            query("SELECT title, imageUrl FROM cached_news_articles").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                assertEquals("旧内容", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
            }
            close()
        }
    }

    @Test
    fun migratesVersionTwoKeepingImagesAndInitializingStockAsNull() = runBlocking {
        val name = "news-stock-migration-test.db"
        migrations.createDatabase(name, 2).apply {
            execSQL("INSERT INTO news_cache (sourceId, fetchedAt) VALUES ('one', 123)")
            execSQL("INSERT INTO cached_news_articles (sourceId, position, id, title, url, summary, imageUrl) VALUES ('one', 0, 'old', 'Title', 'https://example.com/old', 'Summary', 'https://example.com/image.jpg')")
            close()
        }
        migrations.runMigrationsAndValidate(name, 3, true).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val migrated = Room.databaseBuilder(context, AppDataBase::class.java, name).build()
        try {
            val cache = migrated.newsCacheDao().read("one")!!
            assertEquals(123L, cache.metadata.fetchedAt)
            assertEquals("Summary", cache.articles.single().summary)
            assertEquals("https://example.com/image.jpg", cache.articles.single().imageUrl)
            assertNull(cache.articles.single().stockInfo)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun migratesVersionThreeAndKeepsDigestWhenNewsCacheIsCleared() = runBlocking {
        val name = "news-digest-migration-test.db"
        migrations.createDatabase(name, 3).apply {
            execSQL("INSERT INTO news_cache (sourceId, fetchedAt) VALUES ('one', 123)")
            execSQL("INSERT INTO cached_news_articles (sourceId, position, id, title, url, summary, stock_code, stock_price, stock_change, stock_changePercent) VALUES ('one', 0, 'old', 'Title', 'https://example.com', NULL, '00700', 372.8, -3.2, -0.85)")
            close()
        }
        migrations.runMigrationsAndValidate(name, 4, true).close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = NewsDigestEntity(payload = """{"items":[{"url":"https://example.com"}]}""")
        try {
            Room.databaseBuilder(context, AppDataBase::class.java, name).build().let { db ->
                try {
                    assertEquals(372.8, db.newsCacheDao().read("one")!!.articles.single().stockInfo!!.price, 0.0)
                    assertNull(db.newsDigestDao().observeLatest().first())
                    db.newsDigestDao().save(saved)
                    db.newsCacheDao().clearCache()
                    assertEquals(saved, db.newsDigestDao().observeLatest().first())
                } finally { db.close() }
            }
            Room.databaseBuilder(context, AppDataBase::class.java, name).build().let { db ->
                try { assertEquals(saved, db.newsDigestDao().observeLatest().first()) } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun readsInRankOrderAndReplacesOnlyTheRequestedSource() = runBlocking {
        assertNull(dao.read("one"))
        val first = snapshot("one", 10, "z", "a")
        val other = snapshot("two", 20, "other")
        dao.replace(first.copy(articles = first.articles.reversed()))
        dao.replace(other)
        assertEquals(first, dao.read("one"))
        val replacement = snapshot("one", 30, "new")
        dao.replace(replacement)
        assertEquals(replacement, dao.read("one"))
        assertEquals(other, dao.read("two"))
        val empty = snapshot("one", 40)
        dao.replace(empty)
        assertEquals(empty, dao.read("one"))
    }

    @Test
    fun failedReplacementRollsBackBothTimestampAndArticles() = runBlocking {
        val original = snapshot("one", 10, "old")
        dao.replace(original)
        val invalid = snapshot("one", 20, "new").let { it.copy(articles = it.articles + it.articles) }
        try {
            dao.replace(invalid)
            throw AssertionError("Expected duplicate position to fail")
        } catch (_: SQLiteConstraintException) {
            assertEquals(original, dao.read("one"))
        }
    }

    @Test
    fun cacheSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "news-cache-persistence-test.db"
        context.deleteDatabase(name)
        val expected = snapshot("one", 123, "first", "second").let { cache ->
            cache.copy(articles = cache.articles.mapIndexed { index, article ->
                if (index == 0) article.copy(stockInfo = CachedStockInfo("00700", 372.8, -3.2, -0.85))
                else article
            })
        }
        try {
            Room.databaseBuilder(context, AppDataBase::class.java, name).build().let { db ->
                try { db.newsCacheDao().replace(expected) } finally { db.close() }
            }
            Room.databaseBuilder(context, AppDataBase::class.java, name).build().let { db ->
                try {
                    assertEquals(expected, db.newsCacheDao().read("one"))
                    assertEquals(expected.articles, db.newsCacheDao().observeArticles().first())
                    assertEquals(expected.articles, db.newsCacheDao().search("one", "%标题%").first())
                } finally { db.close() }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
