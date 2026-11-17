package com.aster.core.network.sources

import com.aster.core.network.model.SourceCredentials
import com.aster.core.network.model.SourceDefinition

/**
 * 内置来源的统一出口；平台规则独立存放，保留既有来源 ID 和属性
 */
object BuiltInSources {
    val all: List<SourceDefinition> = create()

    /** 不传凭据时 Product Hunt 使用 Feed，微博尝试公开页面。 */
    fun create(credentials: SourceCredentials = SourceCredentials()): List<SourceDefinition> =
        listOf(
            TencentVideoSources.tencentVideo, BaiduSources.baidu,
            AiHotSources.latest,
            BilibiliSources.hotSearch, BilibiliSources.hotVideo, BilibiliSources.ranking,
            CankaoxiaoxiSources.latest,
            ChongbuluoSources.hot, ChongbuluoSources.latest,
            ClsSources.telegraph, ClsSources.depth, ClsSources.hot,
            CoolapkSources.hot,
            DongqiudiSources.latest,
            DoubanSources.hot,
            DouyinSources.hot,
            FastbullSources.express, FastbullSources.news,
            FreebufSources.latest,
            GelonghuiSources.latest,
            GhxiSources.latest,
            GithubSources.trending,
            HackerNewsSources.hot,
            HupuSources.hot,
            IfengSources.hot,
            IqiyiSources.hot,
            IthomeSources.latest,
            Jin10Sources.latest,
            JuejinSources.hot,
            KaopuSources.latest,
            Kr36Sources.quick, Kr36Sources.hot,
            KuaishouSources.hot,
            LinuxDoSources.latest, LinuxDoSources.hot,
            MktnewsSources.flash,
            NowcoderSources.hot,
            PcbetaSources.windows11, PcbetaSources.windows,
            ProductHuntSources.posts(credentials.productHuntToken),
            SmzdmSources.hot,
            SolidotSources.latest,
            SputnikSources.latest,
            SspaiSources.hot,
            SteamSources.hot,
            TencentSources.hot,
            ThePaperSources.hot,
            TiebaSources.hot,
            ToutiaoSources.hot,
            V2exSources.share,
            WallstreetcnSources.quick, WallstreetcnSources.news, WallstreetcnSources.hot,
            WeiboSources.hot(credentials.weiboCookie),
            XueqiuSources.hot,
            ZaobaoSources.latest,
            ZhihuSources.hot
        )
}
