package com.aster.core.network.sources

import com.aster.core.network.model.SourceCredentials
import com.aster.core.network.model.SourceDefinition
import com.aster.core.network.sources.sources.AiHotSources
import com.aster.core.network.sources.sources.BaiduSources
import com.aster.core.network.sources.sources.BilibiliSources
import com.aster.core.network.sources.sources.CankaoxiaoxiSources
import com.aster.core.network.sources.sources.ChongbuluoSources
import com.aster.core.network.sources.sources.ClsSources
import com.aster.core.network.sources.sources.CoolapkSources
import com.aster.core.network.sources.sources.DongqiudiSources
import com.aster.core.network.sources.sources.DoubanSources
import com.aster.core.network.sources.sources.DouyinSources
import com.aster.core.network.sources.sources.FastbullSources
import com.aster.core.network.sources.sources.FreebufSources
import com.aster.core.network.sources.sources.GelonghuiSources
import com.aster.core.network.sources.sources.GhxiSources
import com.aster.core.network.sources.sources.GithubSources
import com.aster.core.network.sources.sources.HackerNewsSources
import com.aster.core.network.sources.sources.HupuSources
import com.aster.core.network.sources.sources.IfengSources
import com.aster.core.network.sources.sources.IqiyiSources
import com.aster.core.network.sources.sources.IthomeSources
import com.aster.core.network.sources.sources.Jin10Sources
import com.aster.core.network.sources.sources.JuejinSources
import com.aster.core.network.sources.sources.KaopuSources
import com.aster.core.network.sources.sources.Kr36Sources
import com.aster.core.network.sources.sources.KuaishouSources
import com.aster.core.network.sources.sources.LinuxDoSources
import com.aster.core.network.sources.sources.MktnewsSources
import com.aster.core.network.sources.sources.NowcoderSources
import com.aster.core.network.sources.sources.PcbetaSources
import com.aster.core.network.sources.sources.ProductHuntSources
import com.aster.core.network.sources.sources.SmzdmSources
import com.aster.core.network.sources.sources.SolidotSources
import com.aster.core.network.sources.sources.SputnikSources
import com.aster.core.network.sources.sources.SspaiSources
import com.aster.core.network.sources.sources.SteamSources
import com.aster.core.network.sources.sources.TencentSources
import com.aster.core.network.sources.sources.TencentVideoSources
import com.aster.core.network.sources.sources.ThePaperSources
import com.aster.core.network.sources.sources.TiebaSources
import com.aster.core.network.sources.sources.ToutiaoSources
import com.aster.core.network.sources.sources.V2exSources
import com.aster.core.network.sources.sources.WallstreetcnSources
import com.aster.core.network.sources.sources.WeiboSources
import com.aster.core.network.sources.sources.XueqiuSources
import com.aster.core.network.sources.sources.ZaobaoSources
import com.aster.core.network.sources.sources.ZhihuSources

/**
 * 内置来源的统一出口；平台规则独立存放，保留既有来源 ID 和属性
 */
object BuiltInSources {
    val all: List<SourceDefinition> = create()

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
