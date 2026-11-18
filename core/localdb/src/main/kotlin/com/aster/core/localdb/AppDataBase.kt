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
abstract class AppDataBase : RoomDatabase() {
    abstract fun newsCacheDao(): NewsCacheDao

    companion object {
        fun create(context: Context): AppDataBase = Room.databaseBuilder(
            context.applicationContext,
            AppDataBase::class.java,
            // 保留已有文件名，重命名入口不丢失已安装应用的数据
            "news.db"
        ).build()
    }
}
