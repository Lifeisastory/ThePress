package com.aster.core.network.model

import com.aster.core.network.adapters.mapping.JsonPath
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface UrlMapping {
    data class Field(val path: JsonPath) : UrlMapping

    /** 首版模板支持 {id}，替换时按 URL 组件编码 */
    data class Template(val value: String) : UrlMapping {
        init {
            require("{id}" in value && !value.replace("{id}", "").contains(Regex("[{}]"))) { "链接模板仅支持 {id}" }
            require(value.replace("{id}", "example").toHttpUrlOrNull() != null) { "链接模板必须是 HTTP/HTTPS URL" }
        }
    }
}
