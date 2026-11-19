# 摘要生成能力

`core:agent` 提供 LLM 调用和新闻摘要生成能力。模块依赖 `core:model` 的共享输入、输出类型，以及 `core:analytics` 的通用请求日志；不依赖 `core:data`、数据库或偏好存储。

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
