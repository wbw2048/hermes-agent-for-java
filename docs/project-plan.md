# 项目计划：Hermes Agent Java 重实现

> **创建日期**: 2026-04-30
> **最后更新**: 2026-05-01
> **当前状态**: 阶段4已完成，阶段5待开始

---

## 1. 项目概述

基于 NousResearch/hermes-agent Python 项目的 Java 重实现学习项目。
通过 Java 重实现实践，深入理解 AI 智能体架构和工程实践。

- **Python 源码**: `hermes-agent/`
- **Java 实现**: 本项目根目录

## 2. 技术栈

| 层次 | 技术 |
|------|------|
| 语言 | Java 21 (Records, Sealed Classes, Pattern Matching) |
| 框架 | Spring Boot 3.x |
| LLM 抽象 | Spring AI |
| 数据访问 | Spring Data JPA + SQLite JDBC |
| Web | Spring Web (REST API) |
| 构建 | Maven (见 `pom.xml`) |
| 前端 | React + TypeScript + Vite (阶段6) |

## 3. 架构设计原则

1. **平台无关核心** — 智能体核心不依赖特定平台
2. **前后端分离** — 后端提供 REST API
3. **依赖注入** — 充分利用 Spring Boot 自动配置和 DI
4. **模块化** — 每个阶段添加的功能可插拔
5. **渐进式复杂度** — 从简单到复杂，每阶段有明确验证标准
6. **学习导向** — 优先理解架构原理，而非追求功能完整性

## 4. 实施阶段

### 阶段概览

| 阶段 | 描述 | 状态 |
|------|------|------|
| 1 | 最简单的对话智能体 (MVP) | ✅ 完成 |
| 2 | 工具调用基础框架 | ✅ 完成 |
| 3 | 更多工具和工具集 | ✅ 完成 |
| 4 | SQLite 会话存储 | ✅ 完成 |
| 5 | 提示构建和上下文压缩 | 🔴 待开始 |
| 6 | Web 界面 | 🔴 待开始 |
| 7 | WebSocket 实时交互 | 🔴 待开始 |
| 8 | 完整功能整合 | 🔴 待开始 |

---

### 阶段1: 最简单的对话智能体 (MVP)

**目标**: 实现只能进行简单问答的 Java 智能体，同时集成 Spring AI 原生工具调用

**实际项目结构**:
```
src/main/java/com/hermes/agent/
├── HermesAgentApplication.java
├── controller/
│   ├── ConversationController.java
│   └── ConversationRequest.java
├── core/
│   └── SimpleAgent.java
└── tool/
    └── builtin/
        ├── DateTimeTools.java
        └── EchoTools.java
src/main/resources/
├── application.yml
└── static/
    └── index.html
```

**实现内容**:
1. Spring Boot 应用主类
2. REST API 控制器 (`/api/conversations`，含 POST/GET/DELETE/Health)
3. `SimpleAgent` 类 — 内存会话管理 + Spring AI `.tools()` 自动工具循环
4. `ConversationRequest` Record — 请求 DTO，含 message 验证
5. `application.yml` — 全量配置（SQLite 数据源预留 + Spring AI OpenAI）
6. `pom.xml` — Spring Boot 3.4.4 + Spring AI 1.1.5 + SQLite JDBC
7. `@Tool` 注解工具实现 — `DateTimeTools`（时间）和 `EchoTools`（回声测试）

**验证标准**:
- [x] Spring Boot 应用成功启动
- [x] `/api/conversations` 端点接收 POST 请求
- [x] 调用 OpenAI 兼容 API 并返回响应
- [x] `@Tool` 注解方法被 Spring AI 自动识别并发送工具定义给 LLM
- [x] 浏览器可访问基本界面（`static/index.html`）

---

### 阶段3: 更多工具和工具集

**目标**: 实现文件操作和终端工具的基础版本，引入工具集系统

**设计文档**: `docs/agent/simple-agent.md` / `docs/tool/builtin/file-tools.md` / `docs/tool/builtin/terminal-tools.md`

**实现内容**:
- [x] 工具集系统 (`@ToolSet` 注解 + `ToolSetManager` + `ToolSetProperties`)
- [x] 文件工具 (`FileTools`: `readFile`, `writeFile`, `patch`, `searchFiles`)
- [x] 路径安全验证 (`PathValidator`)
- [x] 终端工具 (`TerminalTools`: `executeCommand`)
- [x] 工具集配置 (`application.yml`: `hermes.tools.toolsets.active`)

**验证标准**:
- [x] 文件读写工具能正确操作文件系统
- [x] 终端工具执行命令并返回输出
- [x] 工具集可按需启用/禁用
- [x] 安全限制生效 (禁止危险命令、限制文件访问路径)
- [x] 68 个测试全部通过

---

### 阶段4: SQLite 会话存储

**目标**: 实现 SQLite 会话存储

**设计文档**: [docs/session-storage-design.md](docs/session-storage-design.md)

**实现内容**:
- [x] SQLite 数据库设计 (会话表、消息表)
- [x] Spring Data JPA 实体和 Repository
- [x] 会话管理 (创建、加载、持久化)
- [x] SessionStorageService 服务层 (Spring AI Message ↔ DB 实体转换)
- [x] 扩展 REST API (会话列表、删除、标题更新)

**验证标准**:
- [x] 会话能被创建、加载和持久化
- [x] 消息能按顺序存储和检索
- [x] 历史会话能正确恢复
- [x] 79 个测试全部通过

---

### 阶段5: 提示构建和上下文压缩

**目标**: 实现基本的提示构建和上下文管理

**设计文档**: `docs/prompt-and-context-design.md` (待创建)

**实现内容**:
- [ ] 提示构建器 (系统提示组装, 技能和上下文文件支持)
- [ ] 上下文压缩 (历史摘要, 令牌计数和限制)

**验证标准**:
- [ ] 系统提示能正确组装并发送给 LLM
- [ ] 上下文超出限制时能正确压缩
- [ ] 历史摘要不丢失关键信息

---

### 阶段6: Web 界面

**目标**: 实现 Web 管理界面和聊天界面

**设计文档**: `docs/web-ui-design.md` (待创建)

**实现内容**:
- [ ] 后端 REST API (对话、会话管理、配置管理)
- [ ] 前端聊天界面 (类似 ChatGPT)
- [ ] 会话历史面板
- [ ] 工具调用状态显示

**验证标准**:
- [ ] 聊天界面能正常收发消息
- [ ] 会话列表能正确显示和切换
- [ ] 配置管理页面能修改配置

---

### 阶段7: WebSocket 实时交互

**目标**: 实现完整的实时聊天体验

**设计文档**: `docs/websocket-design.md` (待创建)

**实现内容**:
- [ ] WebSocket 消息协议 (认证、消息类型、错误处理)
- [ ] 流式响应 (token-by-token)
- [ ] 工具调用进度实时更新
- [ ] 多客户端并发支持

**验证标准**:
- [ ] 消息实时推送无延迟
- [ ] 流式响应在界面逐步显示
- [ ] 工具调用进度实时可见

---

### 阶段8: 完整功能整合

**目标**: 将所有组件整合，实现基本可用的 Java hermes-agent

**实现内容**:
- [ ] 配置系统完善
- [ ] 错误处理和重试机制
- [ ] 性能优化
- [ ] 完整测试套件

**验证标准**:
- [ ] 所有阶段功能正常运行
- [ ] 异常场景有合理的错误处理
- [ ] 性能满足预期

## 5. 开发流程

1. **文档先行** — 每个阶段先完成 `docs/` 下对应设计文档
2. **TDD** — Red → Green → Refactor
3. **参考 Python** — 理解 Python 实现后再设计 Java 等效实现
4. **每阶段验证** — 完成后进行验证，确保基础功能正常

## 6. 风险评估

| 风险 | 描述 | 缓解 |
|------|------|------|
| 复杂度 | Python 项目功能复杂 | 按阶段拆解，每阶段聚焦有限目标 |
| 技术栈 | Spring AI 相对较新 | 优先使用稳定的 Spring AI 子模块 |
| 学习曲线 | 同时理解 AI 智能体和 Spring 生态 | 学习导向，记录心得 |
| 时间 | 完整重实现需要大量投入 | 渐进式，无固定截止日期 |

## 7. 成功标准

1. 完成阶段 1-4，实现基本的智能体对话和工具调用
2. 理解 hermes-agent 的核心架构和设计模式
3. 能够基于 Java 版本进行功能扩展和定制
4. 形成可复用的 AI 智能体 Java 实现模式
