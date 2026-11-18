package com.aster.thepress

import android.app.Application as AndroidApplication
import com.aster.core.analytics.Analytics
import com.aster.core.data.AppDataContainer

class Application : AndroidApplication() {
    // 整个进程复用数据容器；数据库、偏好和客户端由 core:data 管理
    private val logger by lazy { Analytics.createLogger(this) }
    val dataContainer by lazy { AppDataContainer(this, logger) }
}
