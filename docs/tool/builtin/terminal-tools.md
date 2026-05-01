# TerminalTools 设计文档

## 概述

终端命令执行工具，允许 LLM 在本地执行 shell 命令。当前版本仅支持本地执行，不包含 Docker/Modal/SSH 等远程后端。

## 工具类

```java
@Service
@ToolSet("terminal")
public class TerminalTools {
    // 执行 shell 命令，返回 stdout/stderr/exitCode
    public String executeCommand(String command)
}
```

## 安全限制

| 限制类型 | 实现 |
|---------|------|
| 超时保护 | 默认 60s，可通过 `hermes.tools.terminal.timeout-seconds` 配置 |
| 危险命令拦截 | 匹配 `rm -rf /`, `mkfs`, `dd`, `fdisk`, fork bomb 等模式 |
| 进程隔离 | 每个命令独立进程执行，不保持会话状态 |

## 返回格式

```json
{"exit_code": 0, "stdout": "hello\n", "stderr": ""}
{"error": "Command timed out after 60 seconds: ..."}
{"error": "Refusing to execute potentially dangerous command: ..."}
```

## 依赖关系

- **上游**: Java `ProcessBuilder`
- **下游**: 通过 Spring AI `@Tool` 注解注册到 LLM
- **配置**: `${hermes.tools.terminal.timeout-seconds}` 默认 60

## 测试要点

- 简单命令执行（echo）
- 退出码获取
- stderr 捕获
- 空命令/null 命令处理
- 危险命令拦截（rm -rf /, mkfs, fork bomb）
- 超时保护
