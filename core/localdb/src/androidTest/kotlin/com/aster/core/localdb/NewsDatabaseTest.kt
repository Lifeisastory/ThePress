package com.aster.core.localdb

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aster.core.localdb.dto.NewsCacheDao
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCache
import com.aster.core.localdb.entity.NewsCacheEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewsDatabaseTest {
    private lateinit var database: NewsDatabase
    private lateinit var dao: NewsCacheDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), NewsDatabase::class.java
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
        val expected = snapshot("one", 123, "first", "second")
        try {
            Room.databaseBuilder(context, NewsDatabase::class.java, name).build().let { db ->
                try { db.newsCacheDao().replace(expected) } finally { db.close() }
            }
            Room.databaseBuilder(context, NewsDatabase::class.java, name).build().let { db ->
                try { assertEquals(expected, db.newsCacheDao().read("one")) } finally { db.close() }
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
