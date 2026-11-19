# 摘要生成与 Agent 运行

`core:agent` 提供 LLM 调用、流式 Agent 循环和新闻摘要生成能力。模块依赖 `core:model` 的共享契约、`core:analytics` 的通用请求日志和 `core:network` 的 SSE 传输；不依赖 `core:data`、数据库或偏好存储

## 资讯助手

- `DeepSeekAgentClient` 将 DeepSeek Chat Completions 流转换为文本、工具参数分片和结束事件，要求完整的结束标志后才执行工具
- `runtime/StreamingAgent` 负责模型与工具的多轮循环，合并工具参数、回传工具结果、加载技能和发出 `AgentEvent`，不读取业务仓库
- `runtime/AgentModelClient` 是可替换的模型接口，`AgentTool` 由 data 层注册业务操作
- `skills/AssetAgentSkillStore` 从 `assets/agent_skills/<name>/SKILL.md` 发现技能，`use_skill` 按需读取正文与引用文件
- 首版内置 `news-briefing`、`topic-analysis`，元数据支持单行 `name`、`description`，引用资源限定在技能目录内的 md/txt/json 文件
- 技能使用已注册 Kotlin 工具，不附带 Python/Shell 运行时或第三方技能导入界面

每轮最多十次模型调用、每步最多三十二次工具调用，模型输入上限 120000 字符，单步文本上限 64000 字符，工具参数与结果上限 32000 字符

取消向上传递到模型连接及工具执行，工具参数错误作为失败结果回传模型，连接失败或断流不自动重试，避免重复执行工具

`AGENT_STEP`、`AGENT_TOOL` 使用已有请求日志，仅记录模型、步数和执行结果，不记录 API Key、对话正文或技能全文

## 原有结构化简报

- `LlmClient`：模型调用接口，可注入测试实现；`DeepSeekClient` 负责实际 HTTP 请求与响应解析。
- `generator/DigestGenerator`：构造提示词、调用模型、校验响应、还原引用，并将漏引新闻补充为带有原文标记的条目。
- `model/DigestValidation`：模块内部的响应校验结果，保持 `internal`。
- `model/DigestGenerationException`：可供调用方捕获的生成异常，包含失败原因与诊断信息。

`DigestGenerator.generate(references, settings, key)` 接收新闻引用、生成配置和密钥，返回 `List<DigestItem>`。输入超限或响应校验失败时抛出 `DigestGenerationException`，模型调用失败时传播 `LlmException`，协程取消继续向上传递。生成器不读取配置、不刷新新闻，也不保存摘要；这些操作由 `core:data` 的仓库负责。

`DigestReference`、`DigestSettings`、`DigestItem`、`DigestFailure` 等跨模块类型继续放在 `core:model`。由于生成器的公开 API 使用这些类型及 `Logger`，本模块以 `api` 声明对 `core:model`、`core:analytics` 的依赖。

生成与校验日志使用 `core:analytics` 的 `RequestLogging`，分别记录 `SUMMARIZE` 和 `VALIDATE`，不引用 `data` 的日志转换代码。日志转换函数返回 `LogResponse`，明确区分成功、失败和遗漏补全的 `PARTIAL`；诊断 JSON 只保存业务字段，由框架生成 `outcome`。日志构造及输出异常不会改变模型调用或校验结果。

验证命令：

```powershell
.\gradlew.bat :core:agent:testDebugUnitTest :core:analytics:testDebugUnitTest :core:data:testDebugUnitTest :app:assembleDebug
```

客户端测试保留在本模块，通用请求日志测试位于 `core:analytics`；摘要引用、遗漏补全、异常映射和持久化行为由 `core:data` 的仓库测试覆盖跨模块调用。
