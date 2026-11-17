package com.aster.core.network.sources

import com.aster.core.network.adapters.extract.HtmlDocuments
import com.aster.core.network.adapters.request.FormChallengeRequest
import com.aster.core.network.model.HttpMethod

internal object PcbetaSources {
    private fun source(id: String, name: String, forum: Int) = SourceDefaults.feed(
        id, name, "https://bbs.pcbeta.com/forum.php?mod=rss&fid=$forum&auth=0"
    ).let { definition ->
        definition.copy(
            request = definition.request.copy(acceptedStatusCodes = setOf(403)),
            requestStrategy = FormChallengeRequest(challenge = { response ->
                val body = response.body
                val fields = when {
                    body.contains("<rss") -> null
                    body.contains("action=environment") -> mapOf(
                        "action" to "environment", "platform" to "MacIntel", "webdriver" to "0", "headless" to "0", "cdp" to "0"
                    )
                    body.contains("data-challenge") -> {
                        val challenge = HtmlDocuments.attribute(body, "[data-challenge]", "data-challenge")
                        val distance = HtmlDocuments.attribute(body, "[data-distance]", "data-distance")
                        if (challenge == null || distance == null) null else mapOf(
                            "action" to "slide", "challenge" to challenge, "distance" to distance,
                            "return_to" to (response.url.encodedPath + (response.url.encodedQuery?.let { "?$it" } ?: "")),
                            "client_platform" to "MacIntel", "screen_resolution" to "1920x1080",
                            "webgl_vendor" to "blocked_or_unavailable", "webgl_renderer" to "blocked_or_unavailable"
                        )
                    }
                    else -> null
                }
                fields?.let {
                    SourceDefaults.request("https://bbs.pcbeta.com/__access_review").copy(method = HttpMethod.POST, formBody = it)
                }
            })
        )
    }
    val windows11 = source("pcbeta-windows11", "远景论坛·Windows 11", 563)
    val windows = source("pcbeta-windows", "远景论坛·Windows", 521)
}
