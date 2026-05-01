# EchoTools 设计文档

## 概述

回声工具，原样返回输入消息。用于测试工具调用功能。

## 接口

```java
@Service
public class EchoTools {
    @Tool(description = "Echo back the provided message.")
    public String echo(@ToolParam(description = "The message to echo back") String message)
}
```

- **参数**：`message`（必填）
- **返回**：`{"echoed": "<message>"}`
