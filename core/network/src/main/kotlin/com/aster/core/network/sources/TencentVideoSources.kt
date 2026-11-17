package com.aster.core.network.sources

import com.aster.core.network.adapters.mapping.JsonPath
import com.aster.core.network.model.HttpMethod
import com.aster.core.network.model.HttpRequestSpec
import com.aster.core.network.model.ItemMapping
import com.aster.core.network.model.JsonFieldEquals
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.model.UrlMapping
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object TencentVideoSources {
    val tencentVideo = SourceDefinition(
        id = "qqvideo-tv-hotsearch",
        name = "腾讯视频·电视剧热搜榜",
        request = HttpRequestSpec(
            url = "https://pbaccess.video.qq.com/trpc.vector_layout.page_view.PageService/getCard".toHttpUrl(),
            method = HttpMethod.POST,
            query = mapOf("video_appid" to "3000010", "vversion_platform" to "2"),
            headers = SourceDefaults.browserHeaders + ("Referer" to "https://v.qq.com/"),
            jsonBody = Json.parseToJsonElement(
                """
                {
                  "page_params": {
                    "rank_channel_id": "100113",
                    "rank_name": "HotSearch",
                    "rank_page_size": "30",
                    "tab_mvl_sub_mod_id": "792ac_19e77Sub_1b2",
                    "tab_name": "热搜榜",
                    "tab_type": "hot_rank",
                    "tab_vl_data_src": "f5200deb4596bbf3",
                    "page_id": "scms_shake",
                    "page_type": "scms_shake",
                    "source_key": "", "tag_id": "", "tag_type": "",
                    "new_mark_label_enabled": "1"
                  },
                  "page_context": { "page_index": "1" },
                  "flip_info": {
                    "page_strategy_id": "",
                    "page_module_id": "792ac_19e77",
                    "module_strategy_id": {},
                    "sub_module_id": "20251106065177",
                    "flip_params": {
                      "folding_screen_show_num": "", "is_mvl": "1",
                      "mvl_strategy_info": "{\"default_strategy_id\":\"06755800b45b49238582a6fa1ad0f5c5\",\"default_version\":\"3836\",\"hit_page_uuid\":\"b5080d97dc694a5fb50eb9e7c99326ac\",\"hit_tab_info\":null,\"gray_status_info\":null,\"bypass_to_un_exp_id\":\"\"}",
                      "mvl_sub_mod_id": "20251106065177",
                      "pad_post_show_num": "", "pad_pro_post_show_num": "",
                      "pad_pro_small_hor_pic_display_num": "", "pad_small_hor_pic_display_num": "",
                      "page_id": "scms_shake", "page_num": "0", "page_type": "scms_shake",
                      "post_show_num": "", "shake_size": "", "small_hor_pic_display_num": "",
                      "source_key": "100113",
                      "un_policy_id": "06755800b45b49238582a6fa1ad0f5c5",
                      "un_strategy_id": "06755800b45b49238582a6fa1ad0f5c5"
                    },
                    "relace_children_key": []
                  }
                }
                """.trimIndent()
            )
        ),
        mapping = ItemMapping(
            itemsPath = JsonPath("data.card.children_list.list.cards"),
            idPath = JsonPath("id"),
            titlePath = JsonPath("params.title"),
            url = UrlMapping.Template("https://v.qq.com/x/cover/{id}.html"),
            summaryPath = JsonPath("params.sub_title"),
            requiredRootValues = listOf(JsonFieldEquals(JsonPath("ret"), JsonPrimitive(0))),
            allowEmpty = false
        )
    )

}
