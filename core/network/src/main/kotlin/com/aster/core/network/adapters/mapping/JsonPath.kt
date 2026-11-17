package com.aster.core.network.adapters.mapping

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 支持对象字段和非负数组下标，如 data.cards[0].content；空路径表示根节点 */
class JsonPath(val expression: String) {
    private sealed interface Step {
        data class Field(val name: String) : Step
        data class Index(val value: Int) : Step
    }

    private val steps = buildList {
        var offset = 0
        val tokens = Regex("""([^.\[\]]+)|\[(\d+)]""")
        for (match in tokens.findAll(expression)) {
            val gap = expression.substring(offset, match.range.first)
            val field = match.groups[1]?.value
            if (field != null) {
                require(field.isNotBlank() && gap == if (isEmpty()) "" else ".") { "无效 JSON 路径：$expression" }
                add(Step.Field(field))
            } else {
                require(gap.isEmpty()) { "无效 JSON 路径：$expression" }
                val index = requireNotNull(match.groupValues[2].toIntOrNull()) { "数组下标过大：$expression" }
                add(Step.Index(index))
            }
            offset = match.range.last + 1
        }
        require(offset == expression.length) { "无效 JSON 路径：$expression" }
    }

    fun read(root: JsonElement): JsonElement? {
        var current = root
        for (step in steps) {
            current = when (step) {
                is Step.Field -> (current as? JsonObject)?.get(step.name)
                is Step.Index -> (current as? JsonArray)?.getOrNull(step.value)
            } ?: return null
        }
        return current
    }

    override fun toString(): String = expression
}
