package com.aster.core.model

/** 刷新只报告完成状态；新闻内容始终从数据库观察流读取 */
sealed interface NewsRefreshResult {
    data object Success : NewsRefreshResult

    data class Failure(val reason: NewsFailureReason, val httpCode: Int? = null) : NewsRefreshResult
}
