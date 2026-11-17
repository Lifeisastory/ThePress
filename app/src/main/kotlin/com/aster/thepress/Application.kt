package com.aster.thepress

import android.app.Application as AndroidApplication
import android.content.pm.ApplicationInfo
import android.util.Log
import com.aster.core.data.repository.NewsRepository
import com.aster.core.localdb.NewsDatabase
import com.aster.core.network.NetworkClient
import com.aster.thepress.logging.logSourceFailure

class Application : AndroidApplication() {
    // 整个进程复用一个客户端和来源仓库，页面重建不会重建连接池
    private val networkClient by lazy { NetworkClient() }
    private val newsDatabase by lazy { NewsDatabase.create(this) }
    val newsRepository by lazy {
        NewsRepository(networkClient, newsDatabase.newsCacheDao(), onNetworkError = { sourceId, error ->
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                logSourceFailure(sourceId, error)
            }
        }, onCacheError = { sourceId, error ->
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                Log.w("NewsCache", "Cache access failed for $sourceId", error)
            }
        })
    }
}
