package com.aster.core.network.sources

import com.aster.core.network.model.HtmlField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object Kr36Sources {
    val quick = SourceDefaults.html(
        "36kr-quick", "36氪·快讯", "https://www.36kr.com/newsflashes", ".newsflash-item:has(a.item-title[href])",
        title = HtmlField("a.item-title"), link = HtmlField("a.item-title", "href")
    )
    val hot = SourceDefaults.html(
        "36kr-renqi", "36氪·人气榜", "https://36kr.com/hot-list/renqi", ".article-item-info:has(a.article-item-title[href])",
        title = HtmlField("a.article-item-title.weight-bold"), link = HtmlField("a.article-item-title.weight-bold", "href"),
        summary = HtmlField("a.article-item-description.ellipsis-2")
    ).copy(
        prepareRequest = {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date())
            it.copy(url = "https://36kr.com/hot-list/renqi/$date/1".toHttpUrl())
        }, fallback = quick
    )
}
