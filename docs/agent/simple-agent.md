# SimpleAgent 设计文档

## 概述

核心 AI 智能体，管理会话历史并协调 LLM 调用与工具执行。工具调用循环由 Spring AI 原生 `ToolCallingManager` 自动管理。工具注册由 `ToolSetManager` 按工具集过滤。

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
}
```

## 内部状态

- `ConcurrentHashMap<String, List<Message>> sessionMessages` — 内存会话存储，key 为 sessionId
- `ChatClient chatClient` — Spring AI 聊天客户端
- `Object[] toolObjects` — 经 `ToolSetManager` 过滤后的活跃工具 Bean

## 数据流

```
SimpleAgent.runConversation(sessionId, message)
    ↓
将 UserMessage 追加到 sessionMessages[sessionId]
    ↓
chatClient.prompt()
    .system(defaultSystemPrompt)
    .messages(messages)
    .tools(toolObjects)     ← Spring AI 自动处理工具调用循环
    .call()
    .chatResponse()
    ↓
返回 AssistantMessage 文本，追加到历史
```

工具调用细节（由 Spring AI 内部管理，本类不实现循环逻辑）：
1. LLM 返回 `tool_calls` → Spring AI 自动检测
2. 匹配 `@Tool` 注解方法 → 执行 → 将结果反馈给 LLM
3. 重复直到 LLM 返回非工具调用响应

## 工具集系统

工具通过 `@ToolSet("name")` 注解标记所属工具集，`ToolSetManager` 根据 `application.yml` 中 `hermes.tools.toolsets.active` 列表过滤。

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

## 设计决策

- **不手动实现 `executeToolLoop()`**：直接使用 Spring AI 的 `.tools(toolObjects)` + `ToolCallingManager`，代码量从 ~80 行减到 1 行
- **工具注入用 `List<Object>` 而非 `List<HermesTool>`**：配合 Spring AI `@Tool` 注解，无需自定义接口
- **工具集按配置过滤**：通过 `ToolSetManager` 实现工具的按需启用/禁用，支持不同场景使用不同工具组合
- **`getAvailableTools()` 用反射**：没有 `ToolRegistry`，通过反射扫描 `@Tool` 注解方法获取工具列表

## 依赖关系

- **上游**: `ChatClient.Builder`（Spring AI）、`ToolSetManager`（工具集过滤）、`List<Object>`（所有工具 Bean）
- **下游**: `ConversationController` 调用本类
- **配置**: `${hermes.agent.default-system-prompt}` 通过 `@Value` 注入

## 测试要点

- 无工具调用的正常对话流程
- 会话历史的累积和清除
- 并发会话隔离（ConcurrentHashMap）
- `getAvailableTools()` 反射扫描结果正确
- `ToolSetManager` 按配置正确过滤工具 Bean
