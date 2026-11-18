package com.aster.core.data

import android.content.Context
import com.aster.core.analytics.Logger
import com.aster.core.analytics.NoOpLogger
import com.aster.core.data.repository.DefaultUserPreferencesRepository
import com.aster.core.data.repository.NewsRepository
import com.aster.core.data.repository.OfflineFirstNewsRepository
import com.aster.core.data.repository.UserPreferencesRepository
import com.aster.core.localdb.AppDataBase
import com.aster.core.localsp.AppSharedPreferences
import com.aster.core.network.NetworkClient

/** 由 Application 持有一个实例，统一管理数据依赖；只向外暴露仓库 */
class AppDataContainer(context: Context, private val logger: Logger = NoOpLogger) {
    private val appContext = context.applicationContext
    private val database by lazy { AppDataBase.create(appContext) }
    private val preferences by lazy { AppSharedPreferences(appContext) }
    private val networkClient by lazy { NetworkClient() }

    val newsRepository: NewsRepository by lazy {
        OfflineFirstNewsRepository(networkClient, database.newsCacheDao(), logger = logger)
    }
    val userPreferencesRepository: UserPreferencesRepository by lazy {
        DefaultUserPreferencesRepository(
            sourceOrder = preferences.sourceOrder,
            persistSourceOrder = preferences::saveSourceOrder,
            appearance = preferences.appearance,
            persistThemeMode = preferences::saveThemeMode,
            persistThemeColor = preferences::saveThemeColor,
            logger = logger
        )
    }
}
