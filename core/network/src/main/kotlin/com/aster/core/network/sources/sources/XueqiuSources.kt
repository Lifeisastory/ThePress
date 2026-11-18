package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.adapters.request.CookieBootstrapRequest
import com.aster.core.network.model.JsonFieldEquals
import com.aster.core.network.model.StockMapping
import com.aster.core.network.sources.SourceDefaults
import kotlinx.serialization.json.JsonPrimitive

internal object XueqiuSources {
    val hot = SourceDefaults.json(
        "xueqiu-hotstock", "雪球·热门股票", "https://stock.xueqiu.com/v5/stock/hot_stock/list.json?size=30&_type=10&type=10",
        SourceDefaults.mapping(items = "data.items", id = "code", title = "name", template = "https://xueqiu.com/s/{id}")
            .copy(
                excludedItems = listOf(JsonFieldEquals(JsonPath("ad"), JsonPrimitive(1))),
                stock = StockMapping(JsonPath("code"), JsonPath("current"), JsonPath("chg"), JsonPath("percent"))
            )
    ).copy(requestStrategy = CookieBootstrapRequest(SourceDefaults.request("https://xueqiu.com/hq")))
}
