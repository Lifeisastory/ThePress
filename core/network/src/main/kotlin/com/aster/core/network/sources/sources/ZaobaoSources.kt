package com.aster.core.network.sources.sources

import com.aster.core.network.model.HtmlField
import com.aster.core.network.sources.SourceDefaults
import java.nio.charset.Charset

internal object ZaobaoSources {
    val latest = SourceDefaults.html(
        "zaobao", "早晨报·实时", "https://www.zaochenbao.com/realtime/", "div.list-block > a.item[href]",
        title = HtmlField(".eps"), link = HtmlField(attribute = "href")
    ).let { it.copy(request = it.request.copy(responseCharset = Charset.forName("GB18030"))) }
}
