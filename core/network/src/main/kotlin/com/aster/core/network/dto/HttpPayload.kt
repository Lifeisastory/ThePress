package com.aster.core.network.dto

import okhttp3.Headers
import okhttp3.HttpUrl

/** HTTP 响应 DTO，传递最终 URL、响应头和已读取的正文 */
data class HttpPayload(val url: HttpUrl, val headers: Headers, val body: String)
