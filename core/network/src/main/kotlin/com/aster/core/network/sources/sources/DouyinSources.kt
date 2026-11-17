package com.aster.core.network.sources.sources

import com.aster.core.network.adapters.request.CookieBootstrapRequest
import com.aster.core.network.sources.SourceDefaults

internal object DouyinSources {
    val hot = SourceDefaults.json(
        "douyin", "抖音·热榜",
        "https://www.douyin.com/aweme/v1/web/hot/search/list/?device_platform=webapp&aid=6383&channel=channel_pc_web&detail_list=1",
        SourceDefaults.mapping(items = "data.word_list", id = "sentence_id", title = "word", template = "https://www.douyin.com/hot/{id}")
    ).copy(requestStrategy = CookieBootstrapRequest(SourceDefaults.request("https://login.douyin.com/")))
}
