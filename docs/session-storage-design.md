# 阶段4：SQLite 会话存储设计

## 概述

将会话和消息从内存持久化到 SQLite 数据库，支持应用重启后恢复对话历史。

## 数据库 Schema

### sessions 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | TEXT | PRIMARY KEY | 会话唯一标识 (UUID) |
| title | TEXT | NULLABLE | 会话标题，可空 |
| created_at | TIMESTAMP | NOT NULL | 创建时间 |
| updated_at | TIMESTAMP | NOT NULL | 最后更新时间 |
| message_count | INTEGER | DEFAULT 0 | 消息数量（冗余字段） |

### messages 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 消息唯一标识 |
| session_id | TEXT | NOT NULL, FK → sessions.id | 所属会话 |
| role | TEXT | NOT NULL | 消息角色 (USER/ASSISTANT/SYSTEM/TOOL) |
| content | TEXT | NULLABLE | 消息文本内容 |
| tool_calls | TEXT | NULLABLE | 工具调用信息 (JSON) |
| tool_call_id | TEXT | NULLABLE | 工具调用结果关联 ID |
| timestamp | TIMESTAMP | NOT NULL | 消息时间戳 |
| order_index | INTEGER | NOT NULL | 消息在会话中的顺序 |

### 索引

- `idx_messages_session_order` ON messages(session_id, order_index)

## JPA 实体

- `SessionEntity` — 映射 sessions 表
- `MessageEntity` — 映射 messages 表

## 服务层

### SessionStorageService

核心方法：

| 方法 | 说明 |
|------|------|
| `createSession(sessionId, title?)` | 创建新会话 |
| `saveMessages(sessionId, messages)` | 批量保存消息到会话 |
| `loadSession(sessionId)` | 从数据库加载会话消息（返回 Spring AI Message 列表） |
| `listSessions()` | 列出所有会话摘要 |
| `deleteSession(sessionId)` | 删除会话及其全部消息 |
| `updateSessionTitle(sessionId, title)` | 更新会话标题 |

## Spring AI Message ↔ Entity 转换

- `role` 字符串与 `MessageType` 枚举互转
- `toolCalls` 列表序列化为 JSON 存储，加载时反序列化
- `content` 直接映射

## REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/conversations` | 发送消息（已有） |
| GET | `/api/conversations` | 列出所有会话 |
| GET | `/api/conversations/{sessionId}/history` | 获取会话历史（已有） |
| DELETE | `/api/conversations/{sessionId}/history` | 清除会话历史（已有） |
| DELETE | `/api/conversations/{sessionId}` | 删除整个会话 |
| POST | `/api/conversations/{sessionId}/title` | 更新会话标题 |
