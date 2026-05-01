# 项目设计文档

本目录存放 hermes-agent-for-java 项目的完整设计文档。

`docs/` 层级结构镜像 `src/main/java/com/hermes/agent/` 的包结构，方便 AI 按代码路径定位对应文档。

## 文档索引

### 根包 (`com.hermes.agent`)

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `HermesAgentApplication.java` | [HermesAgentApplication.md](HermesAgentApplication.md) | Spring Boot 入口 |

### `agent/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `SimpleAgent.java` | [agent/simple-agent.md](agent/simple-agent.md) | 核心智能体 |

### `compressor/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `ContextCompressor.java` | [compressor/context-compressor.md](compressor/context-compressor.md) | 上下文压缩器 |
| `TokenEstimator.java` | [compressor/token-estimator.md](compressor/token-estimator.md) | 令牌估算器 |
| `ToolResultPruner.java` | [compressor/tool-result-pruner.md](compressor/tool-result-pruner.md) | 工具结果剪枝 |

### `config/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `ContextCompressionProperties.java` | [config/ContextCompressionProperties.md](config/ContextCompressionProperties.md) | 压缩配置属性 |
| `ToolSetProperties.java` | [config/ToolSetProperties.md](config/ToolSetProperties.md) | 工具集配置属性 |

### `controller/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `ConversationController.java` | [controller/conversation-controller.md](controller/conversation-controller.md) | REST API 控制器 |
| `ConversationRequest.java` | [controller/conversation-request.md](controller/conversation-request.md) | 请求 DTO |

### `entity/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `MessageEntity.java` | [entity/MessageEntity.md](entity/MessageEntity.md) | 消息实体 |
| `SessionEntity.java` | [entity/SessionEntity.md](entity/SessionEntity.md) | 会话实体 |

### `prompt/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `PromptBuilder.java` | [prompt/prompt-builder.md](prompt/prompt-builder.md) | 系统提示构建器 |

### `repository/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `MessageRepository.java` | [repository/MessageRepository.md](repository/MessageRepository.md) | 消息 Repository |
| `SessionRepository.java` | [repository/SessionRepository.md](repository/SessionRepository.md) | 会话 Repository |

### `service/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `SessionStorageService.java` | [service/SessionStorageService.md](service/SessionStorageService.md) | 会话存储服务 |

### `tool/`

| 代码路径 | 设计文档 | 说明 |
|----------|----------|------|
| `ToolSetManager.java` | [tool/toolset-manager.md](tool/toolset-manager.md) | 工具集管理器 |
| `annotation/ToolSet.java` | [tool/annotation/ToolSet.md](tool/annotation/ToolSet.md) | 工具集注解 |
| `builtin/DateTimeTools.java` | [tool/builtin/date-time.md](tool/builtin/date-time.md) | 时间工具 |
| `builtin/EchoTools.java` | [tool/builtin/echo.md](tool/builtin/echo.md) | 回声工具 |
| `builtin/FileTools.java` | [tool/builtin/file-tools.md](tool/builtin/file-tools.md) | 文件工具 |
| `builtin/PathValidator.java` | [tool/builtin/path-validator.md](tool/builtin/path-validator.md) | 路径安全验证 |
| `builtin/TerminalTools.java` | [tool/builtin/terminal-tools.md](tool/builtin/terminal-tools.md) | 终端工具 |

## 全局文档

| 文档 | 说明 |
|------|------|
| [project-plan.md](project-plan.md) | 项目整体计划、阶段划分 |
| [test-plan.md](test-plan.md) | 端到端测试计划 |

## 与代码的对应关系

```
docs/                                      src/main/java/com/hermes/agent/
├── HermesAgentApplication.md         ←    ├── HermesAgentApplication.java
├── agent/                            ←    ├── agent/
│   └── simple-agent.md                    │   └── SimpleAgent.java
├── compressor/                       ←    ├── compressor/
│   ├── context-compressor.md              │   ├── ContextCompressor.java
│   ├── token-estimator.md                 │   ├── TokenEstimator.java
│   └── tool-result-pruner.md              │   └── ToolResultPruner.java
├── config/                           ←    ├── config/
│   ├── ContextCompressionProperties.md    │   ├── ContextCompressionProperties.java
│   └── ToolSetProperties.md               │   └── ToolSetProperties.java
├── controller/                       ←    ├── controller/
│   ├── conversation-controller.md         │   ├── ConversationController.java
│   └── conversation-request.md            │   └── ConversationRequest.java
├── entity/                           ←    ├── entity/
│   ├── MessageEntity.md                   │   ├── MessageEntity.java
│   └── SessionEntity.md                   │   └── SessionEntity.java
├── prompt/                           ←    ├── prompt/
│   └── prompt-builder.md                  │   └── PromptBuilder.java
├── repository/                       ←    ├── repository/
│   ├── MessageRepository.md               │   ├── MessageRepository.java
│   └── SessionRepository.md               │   └── SessionRepository.java
├── service/                          ←    ├── service/
│   └── SessionStorageService.md           │   └── SessionStorageService.java
├── tool/                             ←    ├── tool/
│   ├── toolset-manager.md                 │   ├── ToolSetManager.java
│   ├── annotation/                        │   ├── annotation/
│   │   └── ToolSet.md                     │   │   └── ToolSet.java
│   └── builtin/                           │   └── builtin/
│       ├── date-time.md                   │       ├── DateTimeTools.java
│       ├── echo.md                        │       ├── EchoTools.java
│       ├── file-tools.md                  │       ├── FileTools.java
│       ├── path-validator.md              │       ├── PathValidator.java
│       └── terminal-tools.md              │       └── TerminalTools.java
└── (全局文档)
    ├── project-plan.md
    └── test-plan.md
```

新增代码包时，在 `docs/` 下同步创建对应目录和设计文档。
