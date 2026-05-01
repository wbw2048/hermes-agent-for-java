# Hermes Agent Java 重实现

这是一个基于 [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) Python 项目的 Java 重实现学习项目。

## 项目目标

通过 Java 重实现的实践，深入理解 AI 智能体架构和工程实践。这是一个学习项目，目标不是功能完整性，而是理解架构和设计模式。

## 技术栈

- **Java 21**: 现代 Java 特性 (Records, Sealed Classes, Pattern Matching)
- **Spring Boot 3.4.4**: 依赖注入、配置管理、自动配置
- **Spring AI 1.1.5**: LLM 提供者抽象、工具调用
- **Spring Data JPA**: 数据访问层 (SQLite)
- **Spring Web**: REST API 实现
- **SQLite**: 轻量级数据库
- **Maven**: 构建工具

## 项目结构

```
hermes-agent-for-java/
├── src/main/java/com/hermes/agent/
│   ├── HermesAgentApplication.java      # Spring Boot主类
│   ├── agent/
│   │   └── SimpleAgent.java             # 核心智能体类
│   ├── controller/
│   │   ├── ConversationController.java  # REST API控制器
│   │   └── ConversationRequest.java     # 请求DTO
│   └── tool/builtin/
│       ├── DateTimeTools.java           # 时间工具
│       └── EchoTools.java              # 回声工具
├── src/main/resources/
│   ├── application.yml                  # 配置文件
│   └── static/
│       └── index.html                   # 静态页面
├── docs/                                # 设计文档
└── pom.xml                             # Maven配置文件
```

## 功能特性

### 已完成 (阶段1+2)
- 基本 Spring Boot Web 应用
- REST API: `POST /api/conversations`、健康检查、历史管理
- 集成 Spring AI + OpenAI 兼容接口 (百炼 DashScope)
- `@Tool` 注解原生工具调用（Spring AI 自动管理工具循环）
- 内置工具：`DateTimeTools`、`EchoTools`

### 后续计划
3. 更多工具和工具集
4. SQLite 会话存储
5. 提示构建和上下文压缩
6. Web 界面
7. WebSocket 实时交互
8. 完整功能整合

## 快速开始

### 前提条件
- Java 21+
- Maven 3.6+
- OpenAI API 密钥

### 配置
1. 设置环境变量:
   ```bash
   export OPENAI_API_KEY=your_openai_api_key_here
   ```

2. 或编辑 `src/main/resources/application.yml`:
   ```yaml
   spring:
     ai:
       openai:
         api-key: your_openai_api_key_here
   ```

### 运行
```bash
# 构建项目
mvn clean package

# 运行应用
java -jar target/hermes-agent-0.1.0-SNAPSHOT.jar

# 或者使用 Maven 直接运行
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

### 访问 Web 界面
1. 打开浏览器访问: `http://localhost:8080`
2. 使用聊天界面与 AI 对话
3. API 健康检查: `http://localhost:8080/api/conversations/health`

## API 接口

### 对话接口
```http
POST /api/conversations
Content-Type: application/json

{
  "message": "你好，介绍一下你自己",
  "sessionId": "optional-session-id"
}
```

响应:
```json
{
  "success": true,
  "message": "我是 Hermes Agent，一个基于 Spring Boot 和 Spring AI 的智能助手...",
  "sessionId": "optional-session-id"
}
```

### 健康检查
```http
GET /api/conversations/health
```

### 清除历史
```http
DELETE /api/conversations/{sessionId}/history
```

## 开发

### 代码风格
- 使用 Java 21 新特性 (Records, Pattern Matching)
- 遵循 Spring Boot 最佳实践

### 测试
```bash
# 运行测试
mvn test

# 运行特定测试类
mvn test -Dtest=ConversationControllerTest

# 生成测试覆盖率报告
mvn clean test jacoco:report
```

## 部署

### 生产环境配置
1. 创建 `application-prod.yml`:
   ```yaml
   server:
     port: 9119
   
   spring:
     ai:
       openai:
         api-key: ${OPENAI_API_KEY}
   
   logging:
     level:
       com.hermes.agent: INFO
   ```

2. 使用环境变量管理敏感信息
3. 考虑添加安全配置 (Spring Security)

### Docker 部署
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/hermes-agent-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## 学习目标

通过此项目，你将学习到:

1. **AI 智能体架构**: 理解 hermes-agent 的核心设计
2. **Spring Boot 集成**: 现代 Java Web 应用开发
3. **Spring AI**: 大语言模型集成和提示工程
4. **渐进式开发**: 从简单到复杂的实施策略
5. **前后端分离**: Web 应用架构模式

## 贡献

这是一个学习项目，欢迎通过以下方式参与:

1. 学习 Python hermes-agent 源码
2. 理解并实现各个阶段的功能
3. 编写文档和测试
4. 提出架构改进建议

## 许可证

本项目基于 MIT 许可证开源。

## 致谢

- [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) - 原版 Python 项目
- Spring 团队 - 优秀的 Java 框架
- OpenAI - 提供 AI 模型 API

---

**版本**: 0.1.0-SNAPSHOT  
**状态**: 开发中 (阶段1+2完成)  
**最后更新**: 2026-04-30