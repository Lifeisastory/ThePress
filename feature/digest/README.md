# 简报与资讯助手

`DigestPage` 是简报 Tab 的页面装配，点击左侧历史按钮或向右滑动展开左侧半屏聊天记录，聊天页保持原宽向右移动，两者并排放置，没有覆盖层或 Drawer；向左滑动、返回或选择记录后关闭历史面板

- `DigestAgentViewModel` 管理多会话记录、当前会话、流式事件归并、停止、重试和落盘状态，读取失败或保存失败时禁止覆盖原会话
- 已移除独立的已存简报页面与对应 ViewModel，历史入口只展示聊天记录
- `DigestChatContent` 展示消息流，`DigestComposer` 在采样层外装配可展开输入区，只有点击发送按钮才展开；提交、输入区外点击、列表滚动、导航操作和离开页面都会收回，收回时保留未发送文字
- `DigestSettingsViewModel` 与简报页设置弹窗管理模型、凭据和来源，设置滚动区复用上下渐隐模糊，个人页不展示 Agent 设置
- 页面仅依赖 data 仓库，气泡、历史列表、设置弹窗和欢迎组件位于 `core:ui`，Markdown、玻璃输入、按钮、弹层位于 `core:design_system`
- `MainScreen` 在助手输入期间隐藏底部导航，页面内容同时避让键盘与玻璃输入栏
- 键盘 Insets 由 `DigestPage.imePadding` 在布局阶段统一消费，Main 只提供系统栏和导航栏间距，避免重复避让

内置技能随 `core:agent` 的 assets 打包，首次使用需要点击简报页右上角设置，保存 DeepSeek API Key 并选择资讯来源

新增会话保留原有记录，首次发送时创建独立存储行；生成过程中不能切换会话，第三方 Skills 导入、MCP 和脚本执行环境暂未接入

编译验证命令：

```powershell
.\gradlew.bat :app:assembleDebug :app:compileDebugAndroidTestKotlin :feature:digest:impl:compileDebugUnitTestKotlin
```
