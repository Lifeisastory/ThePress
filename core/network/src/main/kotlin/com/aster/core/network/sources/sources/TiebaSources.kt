package com.aster.core.network.sources.sources

import com.aster.core.network.sources.SourceDefaults

internal object TiebaSources {
    val hot = SourceDefaults.json(
        "tieba", "百度贴吧·热议",
        "https://tieba.baidu.com/hottopic/browse/topicList",
        SourceDefaults.mapping(items = "data.bang_topic.topic_list", id = "topic_id", title = "topic_name", url = "topic_url")
    )
}
