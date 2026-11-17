package com.aster.core.localdb

import android.content.Context
import androidx.room.Database
import androidx.room.AutoMigration
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aster.core.localdb.dto.NewsCacheDao
import com.aster.core.localdb.entity.CachedNewsArticleEntity
import com.aster.core.localdb.entity.NewsCacheEntity

@Database(
    entities = [NewsCacheEntity::class, CachedNewsArticleEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsCacheDao(): NewsCacheDao

    companion object {
        fun create(context: Context): NewsDatabase = Room.databaseBuilder(
            context.applicationContext,
            NewsDatabase::class.java,
            "news.db"
        ).build()
    }
}
