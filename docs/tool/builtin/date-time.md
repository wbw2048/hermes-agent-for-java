# DateTimeTools 设计文档

## 概述

获取当前日期时间的内置工具。

## 接口

```java
@Service
public class DateTimeTools {
    @Tool(description = "Get the current date and time in the specified timezone.")
    public String getCurrentTime(
        @ToolParam(description = "IANA timezone name") String timezone,
        @ToolParam(description = "Date format string") String format
    )
}
```

- **参数**：`timezone`（可选，IANA 时区名，默认系统时区）、`format`（可选，日期格式字符串）
- **返回**：`{"timezone": "...", "current_time": "..."}`
- **错误**：无效时区返回 `{"error": "Invalid timezone: ..."}`
- **注意**：`format` 参数当前被忽略，实际始终使用 `yyyy-MM-dd HH:mm:ss z` 格式
- **日志**：启用 `[TOOL-CALL]` / `[TOOL-RETURN]` 日志记录
