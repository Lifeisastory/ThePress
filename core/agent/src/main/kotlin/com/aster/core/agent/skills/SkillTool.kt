package com.aster.core.agent.skills

import com.aster.core.agent.runtime.AgentTool
import com.aster.core.agent.runtime.AgentToolResult
import com.aster.core.model.AgentStepKind
import kotlinx.serialization.json.*

internal fun skillTool(store: AgentSkillStore, skills: List<AgentSkill>) = AgentTool(
    name = "use_skill",
    title = "加载技能",
    description = "按名称加载技能指令；path 省略时读取 SKILL.md，引用文件路径只能来自已读取的技能正文",
    parameters = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            put("name", buildJsonObject { put("type", "string"); put("description", "可用技能名称") })
            put("path", buildJsonObject { put("type", "string"); put("description", "技能内的相对资源路径") })
        })
        put("required", buildJsonArray { add("name") })
        put("additionalProperties", false)
    },
    kind = AgentStepKind.SKILL
) { args ->
    val name = args["name"]?.jsonPrimitive?.content.orEmpty()
    require(skills.any { it.name == name }) { "技能不存在" }
    val path = args["path"]?.jsonPrimitive?.contentOrNull ?: "SKILL.md"
    require(isSkillResourcePath(path)) { "技能资源路径无效" }
    AgentToolResult(store.read(name, path))
}
