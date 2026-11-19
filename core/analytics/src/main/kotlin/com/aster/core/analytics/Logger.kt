package com.aster.core.analytics

/** 日志调用方只提交事件，输出方式由应用装配。实现应可并发调用且不影响业务流程 */
fun interface Logger {
    fun log(event: LogEvent)
}

object NoOpLogger : Logger {
    override fun log(event: LogEvent) = Unit
}

/** 仅保护同步日志构造和输出；调用方的业务操作不应放进此回调 */
fun Logger.log(event: () -> LogEvent) {
    if (this === NoOpLogger) return
    try {
        log(event())
    } catch (_: Exception) {
        // 不再次写日志，避免日志出口故障引起递归；不捕获 JVM 致命错误。
    }
}
