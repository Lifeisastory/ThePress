package com.aster.core.analytics

import android.content.Context
import android.content.pm.ApplicationInfo

object Analytics {
    fun createLogger(context: Context): Logger = createLogger(
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    )

    fun createLogger(debuggable: Boolean): Logger =
        if (debuggable) LogcatLogger() else NoOpLogger
}
