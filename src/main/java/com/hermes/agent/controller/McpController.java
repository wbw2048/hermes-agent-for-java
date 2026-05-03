package com.hermes.agent.controller;

import com.hermes.agent.config.McpServerProperties;
import com.hermes.agent.config.McpServerProperties.ServerConfig;
import com.hermes.agent.error.StandardErrorResponse;
import com.hermes.agent.mcp.McpConnectionManager;
import com.hermes.agent.mcp.McpConnectionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * MCP 服务器管理 REST API。
 * <p>
 * 提供 MCP 服务器的连接状态查询、连接/断开/重连操作。
 */
@RestController
@RequestMapping("/api/mcp")
@CrossOrigin(origins = "*")
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpConnectionManager connectionManager;
    private final McpServerProperties properties;

    public McpController(McpConnectionManager connectionManager, McpServerProperties properties) {
        this.connectionManager = connectionManager;
        this.properties = properties;
    }

    /**
     * 列出所有配置的 MCP 服务器及其状态。
     */
    @GetMapping("/servers")
    public Map<String, Object> listServers() {
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> servers = new ArrayList<>();

        for (Map.Entry<String, ServerConfig> entry : properties.getServers().entrySet()) {
            String name = entry.getKey();
            ServerConfig config = entry.getValue();
            McpConnectionState state = connectionManager.getState(name);

            Map<String, Object> serverInfo = new LinkedHashMap<>();
            serverInfo.put("name", name);
            serverInfo.put("state", state.name());
            serverInfo.put("command", config.getCommand());
            serverInfo.put("args", config.getArgs());
            serverInfo.put("enabled", config.isEnabled());
            serverInfo.put("toolCount", connectionManager.getDiscoveredTools(name).size());
            serverInfo.put("toolNames", connectionManager.getDiscoveredTools(name));
            serverInfo.put("lastConnectedAt", connectionManager.getLastConnectedAt(name));
            serverInfo.put("lastError", connectionManager.getLastError(name));
            servers.add(serverInfo);
        }

        response.put("success", true);
        response.put("servers", servers);
        return response;
    }

    /**
     * 连接指定 MCP 服务器。
     */
    @PostMapping("/servers/{name}/connect")
    public ResponseEntity<?> connectServer(@PathVariable String name) {
        ServerConfig config = properties.getServers().get(name);
        if (config == null) {
            return ResponseEntity.badRequest().body(
                new StandardErrorResponse("SERVER_NOT_FOUND", "MCP server not found: " + name, null, System.currentTimeMillis(), null));
        }

        McpConnectionState currentState = connectionManager.getState(name);
        if (currentState == McpConnectionState.CONNECTED) {
            return ResponseEntity.badRequest().body(
                new StandardErrorResponse("ALREADY_CONNECTED", "MCP server '" + name + "' is already connected", null, System.currentTimeMillis(), null));
        }

        try {
            connectionManager.connect(name);
            return ResponseEntity.ok(Map.of("success", true, "message", "Connected to '" + name + "'"));
        } catch (Exception e) {
            log.error("Failed to connect MCP server '{}': {}", name, e.getMessage());
            return ResponseEntity.internalServerError().body(
                new StandardErrorResponse("CONNECTION_FAILED", e.getMessage(), null, System.currentTimeMillis(), null));
        }
    }

    /**
     * 断开指定 MCP 服务器。
     */
    @PostMapping("/servers/{name}/disconnect")
    public ResponseEntity<?> disconnectServer(@PathVariable String name) {
        if (!properties.getServers().containsKey(name)) {
            return ResponseEntity.badRequest().body(
                new StandardErrorResponse("SERVER_NOT_FOUND", "MCP server not found: " + name, null, System.currentTimeMillis(), null));
        }

        connectionManager.disconnect(name);
        return ResponseEntity.ok(Map.of("success", true, "message", "Disconnected from '" + name + "'"));
    }

    /**
     * 重连指定 MCP 服务器。
     */
    @PostMapping("/servers/{name}/reconnect")
    public ResponseEntity<?> reconnectServer(@PathVariable String name) {
        if (!properties.getServers().containsKey(name)) {
            return ResponseEntity.badRequest().body(
                new StandardErrorResponse("SERVER_NOT_FOUND", "MCP server not found: " + name, null, System.currentTimeMillis(), null));
        }

        connectionManager.reconnect(name);
        return ResponseEntity.ok(Map.of("success", true, "message", "Reconnecting to '" + name + "' (async)"));
    }

    /**
     * 列出指定服务器的工具。
     */
    @GetMapping("/servers/{name}/tools")
    public Map<String, Object> listServerTools(@PathVariable String name) {
        if (!properties.getServers().containsKey(name)) {
            return Map.of("success", false, "error", "MCP server not found: " + name);
        }

        List<String> tools = connectionManager.getDiscoveredTools(name);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("server", name);
        response.put("toolCount", tools.size());
        response.put("tools", tools);
        return response;
    }

    /**
     * 健康检查。
     */
    @GetMapping("/servers/{name}/health")
    public Map<String, Object> healthCheck(@PathVariable String name) {
        if (!properties.getServers().containsKey(name)) {
            return Map.of("success", false, "error", "MCP server not found: " + name);
        }

        McpConnectionState state = connectionManager.getState(name);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("name", name);
        response.put("state", state.name());
        response.put("healthy", state == McpConnectionState.CONNECTED);
        response.put("lastConnectedAt", connectionManager.getLastConnectedAt(name));
        response.put("lastError", connectionManager.getLastError(name));
        return response;
    }
}
