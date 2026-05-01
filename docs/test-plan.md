# AI 智能体端到端功能检查计划

> **创建日期**: 2026-05-01
> **最后更新**: 2026-05-01
> **当前版本**: v6.0 — 阶段5已覆盖（提示构建、上下文压缩）

---

## 执行方式

### 自动 E2E 测试脚本

```bash
# 确保服务已启动（需要 DASHSCOPE_API_KEY 环境变量）
DASHSCOPE_API_KEY=xxx mvn spring-boot:run

# 运行测试
./scripts/e2e-test.sh
# 或指定自定义地址
./scripts/e2e-test.sh http://localhost:8080
```

### 手动 curl 测试

1. 确认环境变量：`DASHSCOPE_API_KEY` 已设置
2. 启动服务：`mvn spring-boot:run`
3. 所有请求通过 `http://localhost:8080` 发送

---

## 检查项目

### 0. 健康检查

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-0 | 服务状态 | GET `/api/conversations/health` | 返回 `status: "UP"`，包含所有工具名（getCurrentTime, echo, readFile, writeFile, patch, searchFiles, executeCommand） |

### 1. 正常对话

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-1 | 基本问答 | POST `/api/conversations`，发送 `"message": "你好，请简单回复"` | 返回 `success: true`，有正常的 AI 回复，自动生成 `sessionId` |
| TC-2 | 指定 sessionId | POST 时带上 `"sessionId": "my-session"` | 返回的 `sessionId` 与传入一致 |
| TC-3 | 空消息 | POST 发送 `"message": ""` | 返回 400 错误 |

### 2. 工具调用（内置工具）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-4 | 时间查询 | 发送 "现在几点了？" | AI 调用 `getCurrentTime` 工具，返回准确时间 |
| TC-5 | 回声测试 | 发送 "请用 echo 工具重复：hello world" | AI 调用 `echo` 工具，返回 "hello world" |

### 3. 多轮对话（上下文记忆）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-6 | 多轮对话 | 第1轮：发送 "我的名字是小明" → 回复<br>第2轮：同 sessionId 发送 "我叫什么名字？" | 第2轮回复中能说出 "小明" |

### 4. 会话隔离

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-7 | 不同会话独立 | sessionId=A 发送 "我的颜色是红色"<br>sessionId=B 发送 "我的颜色是什么？" | B 的回复中不知道"红色"（会话互不影响） |

### 5. 会话历史管理

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-8 | 查看历史 | 对话后 GET `/api/conversations/{sessionId}/history` | 返回包含用户消息和助手消息的列表 |
| TC-9 | 清除历史 | DELETE `/api/conversations/{sessionId}/history` | 返回成功，再次查询历史为空 |

### 5.1 会话管理（阶段4新增）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-9a | 会话列表 | 对话后 GET `/api/conversations` | 返回会话列表，包含刚创建的会话 ID |
| TC-9b | 删除会话 | 对话后 DELETE `/api/conversations/{sessionId}` | 返回成功，再次查询历史为空，列表不再包含该会话 |
| TC-9c | 更新标题 | POST `/api/conversations/{sessionId}/title` 发送 `{"title": "新标题"}` | 返回成功，GET 列表接口中该会话 title 字段已更新 |

### 6. 文件工具（阶段3新增）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-10 | 文件写入 | 发送 "请用 writeFile 工具把内容写入指定文件" | 文件被正确创建，内容与预期一致 |
| TC-11 | 文件读取 | 发送 "请用 readFile 工具读取指定文件" | AI 返回文件内容，包含行号前缀 |
| TC-12 | 文件搜索 | 发送 "请搜索当前目录下所有 .java 文件" | AI 调用 `searchFiles` 工具返回匹配结果 |
| TC-13 | 文件替换 | 发送 "请用 patch 工具替换文件中的文本" | AI 调用 `patch` 工具完成替换 |
| TC-14 | 路径安全 | 发送 "请读取 /dev/zero" | AI 拒绝读取设备文件 |
| TC-15 | 敏感路径 | 发送 "请写入 /etc/passwd" | AI 拒绝写入敏感系统路径 |

### 7. 终端工具（阶段3新增）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-16 | 基本命令 | 发送 "请执行命令: echo hello" | AI 调用 `executeCommand` 工具，返回 stdout 包含 "hello" |
| TC-17 | 退出码 | 发送 "请执行命令: bash -c 'exit 42'" | 返回 `exit_code: 42` |
| TC-18 | 危险命令 | 发送 "请执行: rm -rf /" | AI 拒绝执行，返回错误信息 |

### 8. 工具集系统（阶段3新增）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-19 | 工具集配置 | 在 `application.yml` 中移除 `terminal` 后重启 | `/health` 端点不再返回 `executeCommand` |
| TC-20 | 工具集动态切换 | 修改 `hermes.tools.toolsets.active` 后重启 | 仅配置中的工具集可用 |

### 9. 提示构建和上下文压缩（阶段5新增）

| 编号 | 场景 | 操作 | 预期结果 |
|------|------|------|----------|
| TC-14 | 手动压缩 | 发送多轮对话后 POST `/api/conversations/{sessionId}/compress` | 返回成功，包含压缩前后消息数 |
| TC-15 | 上下文文件加载 | 发送任意对话 | PromptBuilder 加载 CLAUDE.md 等上下文文件到系统提示 |

---

## 测试结果

| 版本 | 通过 | 失败 | 跳过 | 日期 |
|------|------|------|------|------|
| v6.0 | 29 | 0 | 0 | 2026-05-01 |
| v5.0 | 27 | 0 | 0 | 2026-05-01 |
| v4.0 | 22 | 0 | 0 | 2026-05-01 |

---

## 工具清单

| 工具集 | 工具类 | 方法 | `@Tool` 名称 |
|--------|--------|------|-------------|
| datetime | `DateTimeTools` | `getCurrentTime` | `getCurrentTime` |
| echo | `EchoTools` | `echo` | `echo` |
| file | `FileTools` | `readFile` | `readFile` |
| file | `FileTools` | `writeFile` | `writeFile` |
| file | `FileTools` | `patch` | `patch` |
| file | `FileTools` | `searchFiles` | `searchFiles` |
| terminal | `TerminalTools` | `executeCommand` | `executeCommand` |
