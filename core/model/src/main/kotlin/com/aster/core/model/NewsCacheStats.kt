package com.aster.core.model

/** 资讯文本字段的内容字节数，不包含 SQLite 文件页、图片或网页缓存 */
data class NewsCacheStats(val articleCount: Long, val contentBytes: Long)
