package com.aster.core.network.adapters.request

import java.net.URLEncoder
import java.security.MessageDigest
import okio.ByteString.Companion.encodeUtf8

/** 字节编码与摘要工具；签名字段及其组合顺序由来源配置决定 */
object RequestSigning {
    fun digest(value: String, algorithm: String): String =
        MessageDigest.getInstance(algorithm).digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun base64(value: String): String = value.encodeUtf8().base64()

    fun sortedFormQuery(parameters: Map<String, String>): String =
        parameters.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
}
