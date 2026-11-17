package com.aster.core.network.adapters.extract

import com.aster.core.network.adapters.AdapterException
import com.aster.core.network.dto.HttpPayload
import com.aster.core.network.model.ProcessingStage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** RSS 2.0 / Atom → 统一记录数组；拒绝 DTD，不解析外部实体 */
object FeedExtractor : ContentExtractor {
    override fun extract(response: HttpPayload, json: Json): JsonArray {
        if (response.body.contains(Regex("<!DOCTYPE", RegexOption.IGNORE_CASE))) {
            throw AdapterException(ProcessingStage.EXTRACTION, "Feed 不支持 DTD")
        }
        val document = Jsoup.parse(response.body, response.url.toString(), Parser.xmlParser())
        val root = document.children().firstOrNull()
        val atom = root?.normalName() == "feed"
        if (!atom && root?.normalName() != "rss") {
            throw AdapterException(ProcessingStage.EXTRACTION, "响应不是 RSS 或 Atom")
        }
        val rows = if (atom) root!!.select("entry") else root!!.select("channel > item")
        return JsonArray(rows.map { row ->
            fun text(tag: String) = row.children().firstOrNull { it.normalName() == tag }?.text()?.trim().orEmpty()
            val link = if (atom) {
                row.children().firstOrNull {
                    it.normalName() == "link" && (it.attr("rel").isBlank() || it.attr("rel") == "alternate")
                }?.attr("href").orEmpty()
            } else text("link")
            val description = if (atom) text("summary").ifBlank { text("content") } else text("description")
            val html = Jsoup.parseBodyFragment(description, response.url.toString())
            val image = row.getAllElements().firstOrNull {
                it.normalName() == "media:thumbnail" ||
                    (it.normalName() == "media:content" &&
                        (it.attr("medium") == "image" || it.attr("type").startsWith("image/"))) ||
                    (it.normalName() == "enclosure" && it.attr("type").startsWith("image/"))
            }?.attr("url")?.takeIf { it.isNotBlank() }
                ?: row.children().firstOrNull {
                    atom && it.normalName() == "link" && it.attr("rel") == "enclosure" &&
                        it.attr("type").startsWith("image/")
                }?.attr("href")?.takeIf { it.isNotBlank() }
                ?: html.selectFirst("img[src]")?.attr("src")
            buildJsonObject {
                put("id", text(if (atom) "id" else "guid").ifBlank { link })
                put("title", text("title"))
                put("url", link)
                put("summary", html.text())
                image?.let { put("image", it) }
            }
        })
    }
}
