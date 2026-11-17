package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonDocuments
import com.aster.core.network.adapters.request.RequestSigning
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object CoolapkSources {
    val hot = SourceDefaults.json(
        "coolapk", "酷安·今日热门",
        "https://api.coolapk.com/v6/page/dataList?url=%2Ffeed%2FstatList%3FcacheExpires%3D300%26statType%3Dday%26sortField%3Ddetailnum%26title%3D%E4%BB%8A%E6%97%A5%E7%83%AD%E9%97%A8&title=%E4%BB%8A%E6%97%A5%E7%83%AD%E9%97%A8&subTitle=&page=1",
        SourceDefaults.mapping(), headers = mapOf(
            "X-Requested-With" to "XMLHttpRequest", "X-App-Id" to "com.coolapk.market", "X-Sdk-Int" to "29",
            "X-Sdk-Locale" to "zh-CN", "X-App-Version" to "11.0", "X-Api-Version" to "11", "X-App-Code" to "2101202",
            "User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 10; Redmi K30 5G MIUI/V12.0.3.0.QGICMXM) (#Build; Redmi; Redmi K30 5G; QKQ1.191222.002 test-keys; 10) +CoolMarket/11.0-2101202"
        )
    ).copy(
        prepareRequest = {
            val device = UUID.randomUUID().toString()
            val seconds = (System.currentTimeMillis() + 500) / 1000
            val timeHash = RequestSigning.digest(seconds.toString(), "MD5")
            val value = "token://com.coolapk.market/c67ef5943784d09750dcfbb31020f0ab?$timeHash\$$device&com.coolapk.market"
            val token = RequestSigning.digest(RequestSigning.base64(value), "MD5") + device + "0x" + seconds.toString(16)
            it.copy(headers = it.headers + ("X-App-Token" to token))
        },
        transform = { documents ->
            JsonArray(JsonDocuments.merge(documents, "data").filter { JsonDocuments.text(it, "id") != null }.map {
                val title = JsonDocuments.text(it, "editor_title") ?: JsonDocuments.plainText(JsonDocuments.requireText(it, "message").lineSequence().first())
                val url = JsonDocuments.requireText(it, "url")
                JsonObject(it + mapOf("title" to JsonPrimitive(title), "url" to JsonPrimitive("https://www.coolapk.com$url")))
            })
        }
    )
}
