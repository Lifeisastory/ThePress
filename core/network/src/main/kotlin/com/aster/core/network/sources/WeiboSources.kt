package com.aster.core.network.sources

import com.aster.core.network.model.HtmlField

internal object WeiboSources {
    fun hot(cookie: String?) = SourceDefaults.html(
        "weibo", "微博·热搜", "https://s.weibo.com/top/summary?cate=realtimehot",
        "#pl_top_realtimehot table tbody tr:not(:first-child):has(td.td-02 a[href^=/])",
        title = HtmlField("td.td-02 a[href^=/]"), link = HtmlField("td.td-02 a[href^=/]", "href"),
        itemId = HtmlField("td.td-02 a[href^=/]")
    ).let {
        val headers = it.request.headers + ("Referer" to it.request.url.toString()) +
            if (cookie.isNullOrBlank()) emptyMap() else mapOf("Cookie" to cookie)
        it.copy(request = it.request.copy(headers = headers))
    }
}
