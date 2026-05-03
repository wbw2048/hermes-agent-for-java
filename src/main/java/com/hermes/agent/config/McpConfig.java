package com.hermes.agent.config;

import com.hermes.agent.mcp.McpConnectionManager;
import com.hermes.agent.mcp.McpToolExecutor;
import com.hermes.agent.mcp.McpToolProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 客户端自动配置类。
 * <p>
 * 注册 MCP 连接管理器、工具提供者、工具执行器为 Spring Bean。
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpConfig {

    @Bean
    public McpConnectionManager mcpConnectionManager(McpServerProperties properties) {
        return new McpConnectionManager(properties);
    }

    @Bean
    public McpToolExecutor mcpToolExecutor(McpConnectionManager connectionManager) {
        return new McpToolExecutor(connectionManager);
    }

    @Bean
    public McpToolProvider mcpToolProvider(McpConnectionManager connectionManager,
                                           McpServerProperties properties,
                                           McpToolExecutor toolExecutor) {
        return new McpToolProvider(connectionManager, properties, toolExecutor);
    }
}
