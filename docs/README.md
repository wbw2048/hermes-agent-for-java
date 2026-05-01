# 项目设计文档

本目录存放 hermes-agent-for-java 项目的完整设计文档。

`docs/` 层级结构镜像 `src/main/java/com/hermes/agent/` 的包结构，方便 AI 按代码路径定位对应文档。

## 文档索引

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `HermesAgentApplication.java` | [application.md](application.md) | Spring Boot 入口 |
| `agent/SimpleAgent.java` | [agent/simple-agent.md](agent/simple-agent.md) | 核心智能体 |
| `controller/ConversationController.java` | [controller/conversation-controller.md](controller/conversation-controller.md) | REST API 控制器 |
| `controller/ConversationRequest.java` | [controller/conversation-request.md](controller/conversation-request.md) | 请求 DTO |
| `tool/builtin/DateTimeTools.java` | [tool/builtin/date-time.md](tool/builtin/date-time.md) | 时间工具 |
| `tool/builtin/EchoTools.java` | [tool/builtin/echo.md](tool/builtin/echo.md) | 回声工具 |
| `tool/builtin/FileTools.java` | [tool/builtin/file-tools.md](tool/builtin/file-tools.md) | 文件工具 |
| `tool/builtin/TerminalTools.java` | [tool/builtin/terminal-tools.md](tool/builtin/terminal-tools.md) | 终端工具 |
| `tool/annotation/ToolSet.java` | — | 工具集注解 |
| `tool/ToolSetManager.java` | — | 工具集管理器 |
| `config/ToolSetProperties.java` | — | 工具集配置 |

## 全局文档

| 文档 | 说明 |
|------|------|
| [project-plan.md](project-plan.md) | 项目整体计划、阶段划分 |

## 与代码的对应关系

```
docs/                              src/main/java/com/hermes/agent/
├── application.md            ←    ├── HermesAgentApplication.java
├── agent/                    ←    ├── agent/
│   └── simple-agent.md            │   └── SimpleAgent.java
├── controller/               ←    ├── controller/
│   ├── conversation-controller.md │   ├── ConversationController.java
│   └── conversation-request.md    │   └── ConversationRequest.java
├── tool/                     ←    ├── tool/
│   ├── builtin/                   │   ├── builtin/
│   │   ├── date-time.md           │   │   ├── DateTimeTools.java
│   │   ├── echo.md                │   │   ├── EchoTools.java
│   │   ├── file-tools.md          │   │   ├── FileTools.java
│   │   └── terminal-tools.md      │   │   ├── TerminalTools.java
│   │                              │   │   └── PathValidator.java
│   │                              │   ├── annotation/ToolSet.java
│   │                              │   └── ToolSetManager.java
│   └── config/ToolSetProperties.java
└── project-plan.md
```

新增代码包时，在 `docs/` 下同步创建对应目录和设计文档。
