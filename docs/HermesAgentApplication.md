# HermesAgentApplication 设计文档

## 概述

Spring Boot 应用入口。标准 `@SpringBootApplication`，不排除任何自动配置。

## 说明

- `@SpringBootApplication` + `@EnableConfigurationProperties(ToolSetProperties.class)`
- `ChatClient.Builder` 由 `spring-ai-starter-model-openai` starter 自动提供
- 所有配置集中在 `application.yml` 中完成
- 无需自定义 `ChatClientConfig` 或 `AgentConfig` 类
