# ConversationController 设计文档

## 概述

对话 REST API 控制器，提供发送消息、获取历史、清除会话和健康检查等接口。

## 端点

| 方法 | 路径 | 说明 | 响应格式 |
|------|------|------|----------|
| `POST` | `/api/conversations` | 发送消息 | `{"success": bool, "message": string, "sessionId": string}` |
| `GET` | `/api/conversations/health` | 健康检查 | `{"status": "UP", "service": "hermes-agent", "version": string, "tools": string}` |
| `GET` | `/api/conversations/{sessionId}/history` | 获取历史 | `List<Message>` |
| `DELETE` | `/api/conversations/{sessionId}/history` | 清除历史 | `{"status": "success", "message": string}` |

## 请求处理

- `POST` 请求体为 `ConversationRequest` Record，Spring MVC 自动反序列化
- `sessionId` 缺失时自动生成 UUID
- 异常时返回 500 状态码 + `{"success": false, "error": string, "sessionId": string}`
- 全局启用 `@CrossOrigin(origins = "*")`

## 依赖关系

- **上游**: `SimpleAgent`（通过构造器注入）
- **下游**: HTTP 客户端

## 测试要点

- 正常 POST 请求返回成功响应
- `sessionId` 自动生成
- 异常时返回 500
- 健康检查端点返回工具列表
- GET/DELETE 历史端点正常
