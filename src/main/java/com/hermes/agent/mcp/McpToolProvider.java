package com.hermes.agent.mcp;

import com.hermes.agent.config.McpServerProperties;
import com.hermes.agent.config.McpServerProperties.ServerConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP 工具发现与转换。
 * <p>
 * 从已连接的 MCP 服务器发现工具列表，将其转换为 Spring AI ToolCallback，
 * 以便 SimpleAgent 能够像调用原生 @Tool 一样调用 MCP 工具。
 */
@Component
public class McpToolProvider {

    private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);

    private final McpConnectionManager connectionManager;
    private final McpServerProperties properties;
    private final McpToolExecutor toolExecutor;

    public McpToolProvider(McpConnectionManager connectionManager,
                           McpServerProperties properties,
                           McpToolExecutor toolExecutor) {
        this.connectionManager = connectionManager;
        this.properties = properties;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 发现所有已连接 MCP 服务器的工具，转换为 ToolCallback 列表。
     */
    public List<ToolCallback> discoverAllTools() {
        List<ToolCallback> allCallbacks = new ArrayList<>();
        for (String serverName : properties.getServers().keySet()) {
            allCallbacks.addAll(discoverToolsForServer(serverName));
        }
        log.info("McpToolProvider: discovered {} MCP tool(s) from {} server(s)",
            allCallbacks.size(), properties.getServers().size());
        return allCallbacks;
    }

    /**
     * 发现单个服务器的工具。
     */
    public List<ToolCallback> discoverToolsForServer(String serverName) {
        McpSyncClient client = connectionManager.getClient(serverName);
        if (client == null) {
            log.debug("MCP server '{}' not connected, skipping tool discovery", serverName);
            return List.of();
        }

        try {
            McpSchema.ListToolsResult result = client.listTools();
            if (result == null || result.tools() == null) {
                return List.of();
            }

            List<ToolCallback> callbacks = new ArrayList<>();
            for (McpSchema.Tool tool : result.tools()) {
                if (!shouldIncludeTool(serverName, tool.name())) {
                    continue;
                }

                String callbackName = buildCallbackName(serverName, tool.name());
                String description = tool.description() != null ? tool.description() : "";

                // 扫描注入模式（日志警告，不阻断）
                scanForInjection(serverName, tool.name(), description);

                ToolCallback callback = FunctionToolCallback.builder(callbackName,
                        (java.util.function.Function<String, String>) args ->
                            toolExecutor.callTool(serverName, tool.name(), args))
                    .description(description)
                    .inputSchema(convertMcpSchemaToJsonSchema(tool))
                    .build();

                callbacks.add(callback);
                log.debug("MCP tool registered: server='{}', tool='{}', callback='{}'", serverName, tool.name(), callbackName);
            }
            return callbacks;
        } catch (Exception e) {
            log.warn("Failed to discover tools from MCP server '{}': {}", serverName, e.getMessage());
            return List.of();
        }
    }

    /**
     * 检查工具是否在 include/exclude 过滤范围内。
     */
    boolean shouldIncludeTool(String serverName, String toolName) {
        ServerConfig config = properties.getServers().get(serverName);
        if (config == null) return true;

        List<String> include = config.getIncludeTools();
        if (include != null && !include.isEmpty()) {
            return include.contains(toolName);
        }

        List<String> exclude = config.getExcludeTools();
        if (exclude != null && !exclude.isEmpty()) {
            return !exclude.contains(toolName);
        }

        return true;
    }

    /**
     * 构建全局唯一的回调名称。
     */
    static String buildCallbackName(String serverName, String toolName) {
        String safeServer = sanitize(serverName);
        String safeTool = sanitize(toolName);
        return "mcp_" + safeServer + "_" + safeTool;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper SCHEMA_MAPPER =
        new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 将 MCP Tool 的 inputSchema 转换为 Spring AI 可用的 JSON Schema 字符串。
     */
    static String convertMcpSchemaToJsonSchema(McpSchema.Tool tool) {
        if (tool.inputSchema() == null) {
            return "{}";
        }
        Object schema = tool.inputSchema();
        if (schema instanceof Map<?, ?>) {
            try {
                return SCHEMA_MAPPER.writeValueAsString(schema);
            } catch (Exception e) {
                return "{}";
            }
        }
        if (schema instanceof String s) {
            return s.isEmpty() ? "{}" : s;
        }
        return "{}";
    }

    private static final List<java.util.regex.Pattern> INJECTION_PATTERNS = List.of(
        java.util.regex.Pattern.compile("ignore\\s+(all\\s+)?previous\\s+instructions", java.util.regex.Pattern.CASE_INSENSITIVE),
        java.util.regex.Pattern.compile("you\\s+are\\s+now\\s+a", java.util.regex.Pattern.CASE_INSENSITIVE),
        java.util.regex.Pattern.compile("system\\s*:\\s*", java.util.regex.Pattern.CASE_INSENSITIVE)
    );

    private static void scanForInjection(String serverName, String toolName, String description) {
        if (description == null) return;
        for (var pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(description).find()) {
                log.warn("MCP server '{}' tool '{}': suspicious description — possible prompt injection",
                    serverName, toolName);
                break;
            }
        }
    }
}
