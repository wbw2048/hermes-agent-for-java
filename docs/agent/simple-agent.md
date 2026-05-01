# SimpleAgent 设计文档

## 概述

核心 AI 智能体，管理会话历史并协调 LLM 调用与工具执行。工具调用循环由 Spring AI 原生 `ToolCallingManager` 自动管理。工具注册由 `ToolSetManager` 按工具集过滤。集成上下文压缩功能（阶段5），当对话接近模型令牌限制时自动压缩中间轮次。

## 接口定义

```java
@Component
public class SimpleAgent {
    // 对话入口：追加用户消息 → 调用 LLM → 返回助手响应
    public String runConversation(String sessionId, String userMessage)

    // 清除指定会话历史
    public void clearHistory(String sessionId)

    // 获取会话历史（只读副本）
    public List<Message> getConversationHistory(String sessionId)

    // 通过反射扫描 @Tool 注解方法，返回工具名列表
    public List<String> getAvailableTools()

    // 手动触发指定会话的上下文压缩
    public int compressContext(String sessionId)

    // 获取会话存储服务（供 Controller 调用）
    public SessionStorageService getSessionStorageService()

    // 获取上下文压缩器（供 Controller 调用）
    public ContextCompressor getContextCompressor()
}
```

## 内部状态

- `ChatClient chatClient` — Spring AI 聊天客户端
- `Object[] toolObjects` — 经 `ToolSetManager` 过滤后、且包含 `@Tool` 方法的工具 Bean
- `PromptBuilder promptBuilder` — 系统提示构建器（阶段5引入）
- `ContextCompressor contextCompressor` — 上下文压缩器（阶段5引入）
- `TokenEstimator tokenEstimator` — 令牌估算器（阶段5引入）
- `ContextCompressionProperties compressionProperties` — 压缩配置属性（阶段5引入）
- `SessionStorageService sessionStorageService` — SQLite 持久化会话存储（阶段4引入）

## 数据流

```
SimpleAgent.runConversation(sessionId, message)
    ↓
确保会话存在 (sessionStorageService.createSession)
    ↓
从 SQLite 加载历史 (sessionStorageService.loadSession)
    ↓
将 UserMessage 追加到历史列表
    ↓
构建系统提示 (promptBuilder.buildSystemPrompt)
    ↓
估算令牌数 (tokenEstimator.estimateAll)
    ↓
是否需要压缩？(contextCompressor.shouldCompress)
    ├── 是 → contextCompressor.compress(messages)
    └── 否 → 继续
    ↓
逐条打印消息日志（类型 + 文本）
    ↓
chatClient.prompt()
    .system(systemPrompt)
    .messages(messages)
    .tools(toolObjects)     ← Spring AI 自动处理工具调用循环
    .call()
    .chatResponse()
    ↓
仅保存本轮新增消息 (sessionStorageService.saveMessages)，不重复保存历史
    ↓
返回 AssistantMessage 文本
```

工具调用细节（由 Spring AI 内部管理，本类不实现循环逻辑）：
1. LLM 返回 `tool_calls` → Spring AI 自动检测
2. 匹配 `@Tool` 注解方法 → 执行 → 将结果反馈给 LLM
3. 重复直到 LLM 返回非工具调用响应

## 日志

`runConversation` 启用详细的请求/响应日志：

```
>>> [LLM-REQUEST] sessionId=xxx
>>> [LLM-SYSTEM] 系统提示内容
>>> [LLM-MESSAGES] sessionId=xxx, total=5, estimatedTokens=200
>>> [CONTEXT-COMPRESSION] Triggering compression for sessionId=xxx  (可选)
>>> [CONTEXT-COMPRESSION] After compression: 3 messages             (可选)
  [0] type=USER text=...
  [1] type=ASSISTANT text=...
>>> [LLM-RESPONSE] sessionId=xxx, responseText=...
>>> [LLM-RESPONSE-DETAILS] messageType=ASSISTANT, toolCalls=[...]
```

## 工具集系统

工具通过 `@ToolSet("name")` 注解标记所属工具集，`ToolSetManager` 根据 `application.yml` 中 `hermes.tools.toolsets.active` 列表过滤。注册前还会通过反射检查 Bean 是否包含 `@Tool` 方法，避免无工具的 Bean 导致 Spring AI 报错。

```yaml
hermes:
  tools:
    toolsets:
      active: [datetime, echo, file, terminal]
```

| 工具集 | 工具类 | 工具方法 |
|--------|--------|---------|
| datetime | `DateTimeTools` | `getCurrentTime` |
| echo | `EchoTools` | `echo` |
| file | `FileTools` | `readFile`, `writeFile`, `patch`, `searchFiles` |
| terminal | `TerminalTools` | `executeCommand` |

## 上下文压缩

当估算令牌数 >= `contextLength * thresholdPercent`（默认 128000 * 0.75 = 96000）时自动触发压缩：

1. **预压缩剪枝** — `ToolResultPruner` 去重复工具结果、生成长结果摘要、截断过长 tool_call 参数
2. **保护头部** — 前 N 条消息不参与压缩（默认 3 条）
3. **保护尾部** — 按令牌预算保护最近消息（默认 20000 令牌），从末尾向前累计
4. **LLM 摘要** — 调用 LLM 对中间轮次生成结构化摘要（目标、已完成操作、当前状态等）
5. **组装结果** — `[保护头部] + [摘要消息] + [保护尾部]`

也可通过 `compressContext(sessionId)` 手动触发，或通过 REST API `POST /api/conversations/{sessionId}/compress` 调用。

## 设计决策

- **不手动实现 `executeToolLoop()`**：直接使用 Spring AI 的 `.tools(toolObjects)` + `ToolCallingManager`，代码量从 ~80 行减到 1 行
- **工具注入用 `List<Object>` 而非 `List<HermesTool>`**：配合 Spring AI `@Tool` 注解，无需自定义接口
- **工具 Bean 反射过滤**：避免向 Spring AI 注册不含 `@Tool` 方法的 Bean 导致错误
- **工具集按配置过滤**：通过 `ToolSetManager` 实现工具的按需启用/禁用，支持不同场景使用不同工具组合
- **`getAvailableTools()` 用反射**：没有 `ToolRegistry`，通过反射扫描 `@Tool` 注解方法获取工具列表
- **会话持久化到 SQLite**：阶段4引入 `SessionStorageService`，替代旧的 `ConcurrentHashMap` 内存存储。历史消息从 DB 加载，每轮仅保存新增的 2 条消息（用户 + 助手），不重复保存已有历史
- **保存范围优化**：`saveMessages` 只保存本轮新增消息（`messages.subList(messages.size() - 2)`），避免每轮全量重写历史
- **系统提示简化**：`PromptBuilder` 当前仅返回配置的默认提示词，未实现上下文文件发现和注入防护

## 依赖关系

- **上游**: `ChatClient.Builder`（Spring AI）、`ToolSetManager`（工具集过滤）、`List<Object>`（所有工具 Bean）、`SessionStorageService`（会话持久化）、`PromptBuilder`（提示构建）、`ContextCompressor`（上下文压缩）、`TokenEstimator`（令牌估算）、`ContextCompressionProperties`（压缩配置）
- **下游**: `ConversationController` 调用本类
- **配置**: `${hermes.agent.default-system-prompt}` 通过 `@Value` 注入；压缩相关参数通过 `ContextCompressionProperties` 绑定

## 测试要点

- 无工具调用的正常对话流程
- 会话历史的累积和清除（SQLite 持久化）
- 应用重启后历史可恢复
- 并发会话隔离
- `getAvailableTools()` 反射扫描结果正确
- `ToolSetManager` 按配置正确过滤工具 Bean
- 上下文压缩触发条件正确
- 压缩后消息保持头部和尾部保护
- 手动压缩端点正常工作
