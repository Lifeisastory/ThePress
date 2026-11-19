package com.aster.core.model

/** 资讯刷新的业务失败分类；底层异常和响应详情保留在 NetworkError 中 */
enum class NewsFailureReason {
    CONNECTION,
    TIMEOUT,
    HTTP,
    INVALID_DATA,
    STORAGE
}
