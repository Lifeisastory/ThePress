package com.aster.core.agent.skills

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AgentSkill(val name: String, val description: String)

interface AgentSkillStore {
    suspend fun list(): List<AgentSkill>
    suspend fun read(name: String, path: String = "SKILL.md"): String
}

/** 内置技能从 assets 加载，正文与引用资源按需读取 */
class AssetAgentSkillStore(private val assets: AssetManager) : AgentSkillStore {
    override suspend fun list(): List<AgentSkill> = withContext(Dispatchers.IO) {
        assets.list(ROOT).orEmpty().sorted().map { directory ->
            val content = read(directory)
            val skill = parseSkillMetadata(content)
            require(skill.name == directory) { "技能名称与目录不一致" }
            skill
        }
    }

    override suspend fun read(name: String, path: String): String = withContext(Dispatchers.IO) {
        require(name.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) { "技能名称无效" }
        require(isSkillResourcePath(path)) { "技能资源路径无效" }
        assets.open("$ROOT/$name/$path").bufferedReader().use { reader ->
            val content = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val size = reader.read(buffer)
                if (size < 0) break
                content.append(buffer, 0, size)
                require(content.length <= 32_000) { "技能文件过大" }
            }
            content.toString()
        }
    }

    private companion object { const val ROOT = "agent_skills" }
}

internal fun isSkillResourcePath(path: String): Boolean =
    path.isNotBlank() && path.length <= 200 && !path.contains('\\') && !path.contains(':') &&
        path.split('/').all { it.isNotBlank() && it != "." && it != ".." } &&
        (path.endsWith(".md") || path.endsWith(".txt") || path.endsWith(".json"))

/** 首版内置技能使用单行 name/description 元数据，正文不参与元数据解析 */
internal fun parseSkillMetadata(content: String): AgentSkill {
    val lines = content.lineSequence().toList()
    require(lines.firstOrNull()?.trim() == "---") { "技能缺少元数据" }
    val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
    require(end >= 0) { "技能元数据未闭合" }
    val fields = lines.drop(1).take(end).mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator < 0) null else line.take(separator).trim() to line.drop(separator + 1).trim().trim('"', '\'')
    }.toMap()
    val name = fields["name"].orEmpty()
    val description = fields["description"].orEmpty()
    require(name.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) && name.length <= 64)
    require(description.isNotBlank() && description.length <= 1024)
    return AgentSkill(name, description)
}
