package com.aster.core.network.sources

internal object IqiyiSources {
    val hot = SourceDefaults.json(
        "iqiyi-hot-ranklist", "爱奇艺·热播榜",
        "https://mesh.if.iqiyi.com/portal/lw/v7/channel/card/videoTab?channelName=recommend&data_source=v7_rec_sec_hot_rank_list&tempId=85&count=30&block_id=hot_ranklist&device=14a4b5ba98e790dce6dc07482447cf48&from=webapp",
        SourceDefaults.mapping(items = "items[0].video[0].data", id = "entity_id", url = "page_url", summary = "description"),
        headers = mapOf("Referer" to "https://www.iqiyi.com")
    )
}
