package com.aster.core.network.adapters.extract

import org.jsoup.Jsoup

object HtmlDocuments {
    fun attribute(html: String, selector: String, attribute: String): String? =
        Jsoup.parse(html).selectFirst(selector)?.attr(attribute)?.takeIf { it.isNotBlank() }
}
