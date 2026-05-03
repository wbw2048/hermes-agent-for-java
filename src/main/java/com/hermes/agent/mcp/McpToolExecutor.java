package com.hermes.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.config.McpServerProperties.ServerConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * MCP 工具调用代理。
 * <p>
 * 将 MCP 工具调用委托给 McpConnectionManager 中的客户端执行，
 * 处理错误恢复和凭证脱敏。
 */
@Component
public class McpToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);

    /** 凭证模式匹配 */
    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
        "(?:" +
        "ghp_[A-Za-z0-9_]{1,255}" +
        "|sk-[A-Za-z0-9_]{1,255}" +
        "|Bearer\\s+\\S+" +
        "|token=[^\\s&,;\"']{1,255}" +
        "|key=[^\\s&,;\"']{1,255}" +
        "|API_KEY=[^\\s&,;\"']{1,255}" +
        "|password=[^\\s&,;\"']{1,255}" +
        "|secret=[^\\s&,;\"']{1,255}" +
        ")",
        Pattern.CASE_INSENSITIVE
    );

    private final McpConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public McpToolExecutor(McpConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 MCP 工具。接收 JSON 字符串参数，返回 JSON 字符串结果。
     * 匹配 Spring AI ToolCallback.call(String) 签名。
     */
    public String callTool(String serverName, String toolName, String argumentsJson) {
        try {
            McpSyncClient client = connectionManager.getClient(serverName);
            if (client == null) {
                return errorResponse("MCP server '" + serverName + "' is not connected");
            }

            Map<String, Object> args = parseArguments(argumentsJson);
            McpSchema.CallToolRequest callRequest = new McpSchema.CallToolRequest(toolName, args);

            McpSchema.CallToolResult result = client.callTool(callRequest);
            return formatToolResult(result);

        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return errorResponse(sanitizeCredentials(message));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse MCP tool arguments: {}", e.getMessage());
            return Map.of();
        }
    }

    private String formatToolResult(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return "{\"success\": true, \"content\": \"\"}";
        }
        StringBuilder sb = new StringBuilder();
        if (result.isError()) {
            sb.append("{\"success\": false, \"error\": ");
        } else {
            sb.append("{\"success\": true, \"content\": ");
        }

        List<String> textParts = new ArrayList<>();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                textParts.add(textContent.text());
            } else {
                textParts.add(content.toString());
            }
        }
        String combined = String.join("\n", textParts);
        sb.append("\"").append(escapeJson(combined)).append("\"}");
        return sb.toString();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    static String sanitizeCredentials(String text) {
        if (text == null) return null;
        return CREDENTIAL_PATTERN.matcher(text).replaceAll("[REDACTED]");
    }

    private String errorResponse(String error) {
        return "{\"success\": false, \"error\": \"" + escapeJson(error) + "\"}";
    }
}
