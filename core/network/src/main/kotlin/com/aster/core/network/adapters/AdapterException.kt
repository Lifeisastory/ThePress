package com.aster.core.network.adapters

import com.aster.core.network.model.ProcessingStage

/** 工具实现用此异常报告可预期的响应处理失败 */
class AdapterException(
    val stage: ProcessingStage,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
