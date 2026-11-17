
[ui](../ui) 存放与业务有关的 UI。

`NewsArticleCard` 直接接收 `core:model` 中的 `NewsArticle`、`NewsSource` 和点击回调，从模型读取标题、来源名、摘要及可选配图地址。调用方应传入与 `article.sourceId` 对应的来源。卡片采用文字配右侧缩略图的布局，无图时文字占满宽度；Coil 加载中和失败时保持缩略图占位尺寸。数据查询和打开原文由调用方负责。
