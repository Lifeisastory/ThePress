package com.aster.core.model

/** 保留响应头顺序和重复值，不依赖具体 HTTP 客户端 */
data class HttpHeaders(val entries: List<Pair<String, String>> = emptyList()) {
    /** 与 HTTP 头语义一致：名称不区分大小写，单值查询取最后一个值 */
    operator fun get(name: String): String? =
        entries.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second

    fun values(name: String): List<String> =
        entries.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }
}
