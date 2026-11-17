package com.aster.core.analytics

/** 日志调用方只提交事件，输出方式由应用装配。实现应可并发调用且不影响业务流程 */
fun interface Logger {
    fun log(event: LogEvent)
}

object NoOpLogger : Logger {
    override fun log(event: LogEvent) = Unit
}
