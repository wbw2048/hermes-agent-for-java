# ConversationRequest 设计文档

## 概述

对话请求 DTO Record，携带用户消息和可选的会话 ID。

## 定义

```java
public record ConversationRequest(
    @JsonProperty("message") String message,
    @JsonProperty("sessionId") String sessionId
)
```

## 验证

Record 紧凑构造器中：
- `message` 为 null 或空 → 抛 `IllegalArgumentException`
- `sessionId` 为 null → 规范化为空字符串 `""`

## 测试要点

- 正常构造（message + sessionId）
- message 为空抛异常
- sessionId 为 null 时规范化为空字符串
