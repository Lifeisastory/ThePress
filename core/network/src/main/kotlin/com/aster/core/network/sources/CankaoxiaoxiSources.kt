package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.adapters.request.BatchRequests
import kotlinx.serialization.json.JsonArray

internal object CankaoxiaoxiSources {
    // 使用 HTTPS，避免要求宿主放开全局明文流量
    val latest = SourceDefaults.json(
        "cankaoxiaoxi", "参考消息", "https://china.cankaoxiaoxi.com/json/channel/zhongguo/list.json",
        SourceDefaults.mapping(id = "data.id", title = "data.title", url = "data.url")
    ).copy(
        requestStrategy = BatchRequests(listOf("guandian", "gj").map {
            SourceDefaults.request("https://china.cankaoxiaoxi.com/json/channel/$it/list.json")
        }),
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "list").sortedByDescending { JsonDocuments.text(it, "data.publishTime").orEmpty() })
        }
    )
}
