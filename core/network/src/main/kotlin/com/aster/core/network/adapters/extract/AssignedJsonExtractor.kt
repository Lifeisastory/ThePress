package com.aster.core.network.adapters.extract

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.HttpPayload
import com.aster.core.model.ProcessingStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** 提取变量赋值中的 JSON，按括号和字符串边界扫描；不求值 JavaScript */
class AssignedJsonExtractor(variable: String) : ContentExtractor {
    private val assignment = Regex("(?:^|[;\\s>])(?:var\\s+|let\\s+|const\\s+)?${Regex.escape(variable)}\\s*=\\s*")

    override fun extract(response: HttpPayload, json: Json): JsonElement {
        val match = assignment.find(response.body) ?: fail("未找到 JSON 变量赋值")
        val start = match.range.last + 1
        if (response.body.getOrNull(start) !in listOf('{', '[')) fail("变量值不是 JSON 对象或数组")
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until response.body.length) {
            val char = response.body[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> depth++
                '}', ']' -> {
                    depth--
                    if (depth == 0) return JsonExtractor.extract(response.copy(body = response.body.substring(start, index + 1)), json)
                }
            }
        }
        fail("JSON 变量值未结束")
    }

    private fun fail(message: String): Nothing = throw AdapterException(ProcessingStage.EXTRACTION, message)
}
