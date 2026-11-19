
[ui](../ui) 存放与业务有关的 UI。

`AgentMessageCard` 使用按内容定宽、左右错开的 Markdown 气泡，工具与技能采用“调用列表 → 单次详情”两级折叠；`AgentConversationPanel` 装配半屏历史标题和 `AgentConversationList`，`DigestSettingsDialog` 复用玻璃弹窗、上下渐隐模糊和原设置内容，`DigestAgentWelcome` 展示空会话与快捷问题，这些组件只接收数据和回调，不持有仓库或执行网络请求

`NewsArticleCard` 直接接收 `core:model` 中的 `NewsArticle`、`NewsSource` 和点击回调，从模型读取标题、来源名、摘要及可选配图地址。调用方应传入与 `article.sourceId` 对应的来源。卡片采用文字配右侧缩略图的布局，无图时文字占满宽度；Coil 加载中和失败时保持缩略图占位尺寸。数据查询和打开原文由调用方负责。
