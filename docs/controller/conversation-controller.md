# ConversationController 设计文档

## 概述

对话 REST API 控制器，提供发送消息、获取历史、会话管理、健康检查和上下文压缩等接口。

## 端点

| 方法 | 路径 | 说明 | 响应格式 |
|------|------|------|----------|
| `POST` | `/api/conversations` | 发送消息 | `{"success": bool, "message": string, "sessionId": string}` |
| `GET` | `/api/conversations` | 列出所有会话 | `List<SessionEntity>` |
| `GET` | `/api/conversations/health` | 健康检查 | `{"status": "UP", "service": "hermes-agent", "version": string, "tools": string}` |
| `GET` | `/api/conversations/{sessionId}/history` | 获取历史 | `List<Message>` |
| `DELETE` | `/api/conversations/{sessionId}/history` | 清除历史 | `{"status": "success", "message": string}` |
| `DELETE` | `/api/conversations/{sessionId}` | 删除整个会话 | `{"status": "success", "message": string}` |
| `POST` | `/api/conversations/{sessionId}/title` | 更新会话标题 | `{"status": "success", "message": string}` 或 `{"error": string}` |
| `POST` | `/api/conversations/{sessionId}/compress` | 手动触发压缩 | `{"success": bool, "sessionId": string, "messagesBefore": int, "messagesAfter": int, "messagesRemoved": int}` |

## 请求处理

- `POST` 请求体为 `ConversationRequest` Record，Spring MVC 自动反序列化
- `sessionId` 缺失时自动生成 UUID
- 更新标题和手动压缩端点接收 JSON Body（`Map<String, String>`）
- 异常时返回 500 状态码 + `{"success": false, "error": string, "sessionId": string}`
- 全局启用 `@CrossOrigin(origins = "*")`
- `SimpleAgent` 通过 `@Lazy` 注入避免循环依赖

## 新增端点说明（阶段4 & 阶段5）

### 会话管理（阶段4）

- **GET /api/conversations** — 列出所有会话，按 `updatedAt` 倒序
- **DELETE /api/conversations/{sessionId}** — 删除整个会话（包括消息记录）
- **POST /api/conversations/{sessionId}/title** — 更新会话标题，Body: `{"title": "新标题"}`

### 上下文压缩（阶段5）

- **POST /api/conversations/{sessionId}/compress** — 手动触发上下文压缩，返回压缩前后的消息数量对比

## 依赖关系

- **上游**: `SimpleAgent`（通过 `@Lazy` 构造器注入）
- **下游**: HTTP 客户端

## 测试要点

- 正常 POST 请求返回成功响应
- `sessionId` 自动生成
- 异常时返回 500
- 健康检查端点返回工具列表
- GET/DELETE 历史端点正常
- 列出所有会话返回正确排序
- 删除会话同时清除消息记录
- 更新会话标题后能正确反映
- 手动压缩端点能正确触发压缩并返回统计
- 不存在会话时压缩/更新标题返回 404
