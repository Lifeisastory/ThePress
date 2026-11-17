package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.adapters.request.RequestSigning
import com.aster.core.network.model.HttpRequestSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object ClsSources {
    private fun signed(request: HttpRequestSpec, telegraph: Boolean): HttpRequestSpec {
        val parameters = mapOf("appName" to "CailianpressWeb", "os" to "web", "sv" to "7.7.5") + request.query +
            if (telegraph) mapOf("last_time" to (System.currentTimeMillis() / 1000).toString(), "refresh_type" to "1", "rn" to "30") else emptyMap()
        val sign = RequestSigning.digest(RequestSigning.digest(RequestSigning.sortedFormQuery(parameters), "SHA-1"), "MD5")
        return request.copy(query = parameters + ("sign" to sign))
    }

    private fun source(id: String, name: String, endpoint: String, path: String, telegraph: Boolean = false) = SourceDefaults.json(
        id, name, endpoint, SourceDefaults.mapping(template = "https://www.cls.cn/detail/{id}"),
        headers = mapOf("Referer" to "https://www.cls.cn/telegraph")
    ).copy(
        prepareRequest = { signed(it, telegraph) },
        transform = { documents ->
            val items = JsonDocuments.merge(documents, path).filter { !telegraph || it["is_ad"] != JsonPrimitive(1) }
            val sorted = if (id == "cls-depth") items.sortedByDescending { JsonDocuments.text(it, "ctime")?.toLongOrNull() ?: 0 } else items
            JsonArray(sorted.map { JsonObject(it + ("title" to JsonPrimitive(JsonDocuments.requireText(it, "title", "brief")))) })
        }
    )
    val telegraph = source("cls-telegraph", "财联社·电报", "https://www.cls.cn/v1/roll/get_roll_list", "data.roll_data", true)
    val depth = source("cls-depth", "财联社·深度", "https://www.cls.cn/v3/depth/home/assembled/1000", "data.depth_list")
    val hot = source("cls-hot", "财联社·热门", "https://www.cls.cn/v2/article/hot/list", "data")
}
