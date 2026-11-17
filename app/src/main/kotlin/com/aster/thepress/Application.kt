package com.aster.thepress

import android.app.Application as AndroidApplication
import com.aster.core.analytics.Analytics
import com.aster.core.data.repository.NewsRepository
import com.aster.core.localdb.NewsDatabase
import com.aster.core.network.NetworkClient

class Application : AndroidApplication() {
    // 整个进程复用一个客户端和来源仓库，页面重建不会重建连接池
    private val logger by lazy { Analytics.createLogger(this) }
    private val networkClient by lazy { NetworkClient() }
    private val newsDatabase by lazy { NewsDatabase.create(this) }
    val newsRepository by lazy {
        NewsRepository(networkClient, newsDatabase.newsCacheDao(), logger = logger)
    }
}
