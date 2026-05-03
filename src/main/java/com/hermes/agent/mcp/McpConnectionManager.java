package com.hermes.agent.mcp;

import com.hermes.agent.config.McpServerProperties;
import com.hermes.agent.config.McpServerProperties.ServerConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * MCP 服务器连接生命周期管理器。
 * <p>
 * 负责建立、断开、重连 stdio 模式的 MCP 客户端，管理连接状态。
 * 启动时自动连接所有 enabled=true 的服务器。
 */
@Component
public class McpConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionManager.class);
    private static final int MAX_RECONNECT_RETRIES = 5;
    private static final long MAX_BACKOFF_SECONDS = 60;

    /** 环境变量白名单 */
    private static final Set<String> SAFE_ENV_KEYS = Set.of(
        "PATH", "HOME", "USER", "LANG", "LC_ALL", "TERM", "SHELL", "TMPDIR"
    );

    private final McpServerProperties properties;

    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, McpConnectionState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastConnectedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastError = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<String>> discoveredTools = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mcp-connection");
        t.setDaemon(true);
        return t;
    });

    public McpConnectionManager(McpServerProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void connectAll() {
        Map<String, ServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("McpConnectionManager: no MCP servers configured");
            return;
        }
        log.info("McpConnectionManager: connecting to {} configured server(s)", servers.size());
        for (Map.Entry<String, ServerConfig> entry : servers.entrySet()) {
            if (entry.getValue().isEnabled()) {
                connectAsync(entry.getKey());
            } else {
                states.put(entry.getKey(), McpConnectionState.DISCONNECTED);
                log.info("MCP server '{}' is disabled, skipping", entry.getKey());
            }
        }
    }

    /**
     * 异步连接单个服务器。
     */
    private void connectAsync(String serverName) {
        executor.submit(() -> connect(serverName));
    }

    /**
     * 连接指定 MCP 服务器。
     */
    public void connect(String serverName) {
        ServerConfig config = properties.getServers().get(serverName);
        if (config == null) {
            throw new IllegalArgumentException("MCP server not found: " + serverName);
        }
        if (config.getCommand() == null || config.getCommand().isBlank()) {
            throw new IllegalArgumentException("MCP server '" + serverName + "' has no command configured");
        }

        states.put(serverName, McpConnectionState.CONNECTING);
        lastError.remove(serverName);

        for (int attempt = 1; attempt <= MAX_RECONNECT_RETRIES; attempt++) {
            try {
                log.info("Connecting to MCP server '{}' (attempt {}/{})", serverName, attempt, MAX_RECONNECT_RETRIES);
                McpSyncClient client = createClient(serverName, config);
                clients.put(serverName, client);
                states.put(serverName, McpConnectionState.CONNECTED);
                lastConnectedAt.put(serverName, Instant.now());
                discoveredTools.put(serverName, listToolNames(client));
                log.info("MCP server '{}' connected, {} tools available", serverName, discoveredTools.get(serverName).size());
                return;
            } catch (Exception e) {
                lastError.put(serverName, e.getMessage());
                log.warn("MCP server '{}' connection attempt {} failed: {}", serverName, attempt, e.getMessage());
                if (attempt < MAX_RECONNECT_RETRIES) {
                    long backoff = Math.min((long) Math.pow(2, attempt) * 1000, MAX_BACKOFF_SECONDS * 1000);
                    try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        states.put(serverName, McpConnectionState.ERROR);
        log.error("MCP server '{}' failed to connect after {} attempts", serverName, MAX_RECONNECT_RETRIES);
    }

    /**
     * 断开指定服务器。
     */
    public void disconnect(String serverName) {
        McpSyncClient client = clients.remove(serverName);
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Error closing MCP client for '{}': {}", serverName, e.getMessage());
            }
        }
        states.put(serverName, McpConnectionState.DISCONNECTED);
        discoveredTools.remove(serverName);
        log.info("MCP server '{}' disconnected", serverName);
    }

    /**
     * 重连指定服务器（指数退避）。
     */
    public void reconnect(String serverName) {
        disconnect(serverName);
        states.put(serverName, McpConnectionState.CONNECTING);
        executor.submit(() -> connect(serverName));
    }

    /**
     * 获取已连接的客户端。
     */
    public McpSyncClient getClient(String serverName) {
        McpSyncClient client = clients.get(serverName);
        if (client == null || states.get(serverName) != McpConnectionState.CONNECTED) {
            return null;
        }
        return client;
    }

    public McpConnectionState getState(String serverName) {
        return states.getOrDefault(serverName, McpConnectionState.DISCONNECTED);
    }

    public String getLastError(String serverName) {
        return lastError.get(serverName);
    }

    public Instant getLastConnectedAt(String serverName) {
        return lastConnectedAt.get(serverName);
    }

    public List<String> getDiscoveredTools(String serverName) {
        return discoveredTools.getOrDefault(serverName, List.of());
    }

    /**
     * 返回所有服务器的状态快照。
     */
    public Map<String, ServerStatus> getAllStatuses() {
        Map<String, ServerStatus> result = new LinkedHashMap<>();
        for (String name : properties.getServers().keySet()) {
            ServerConfig config = properties.getServers().get(name);
            result.put(name, new ServerStatus(
                name,
                getState(name),
                config.getCommand(),
                config.getArgs(),
                getDiscoveredTools(name).size(),
                getDiscoveredTools(name),
                getLastConnectedAt(name),
                getLastError(name),
                config.isEnabled()
            ));
        }
        return result;
    }

    @PreDestroy
    public void shutdownAll() {
        log.info("Shutting down all MCP connections...");
        for (String name : new ArrayList<>(clients.keySet())) {
            disconnect(name);
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        states.clear();
        log.info("All MCP connections shut down");
    }

    private McpSyncClient createClient(String serverName, ServerConfig config) {
        Map<String, String> safeEnv = buildSafeEnv(config.getEnv());

        ServerParameters params = ServerParameters.builder(config.getCommand())
            .args(config.getArgs())
            .env(safeEnv)
            .build();

        StdioClientTransport transport = new StdioClientTransport(
            params, io.modelcontextprotocol.json.McpJsonMapper.getDefault());

        Duration connectTimeout = Duration.ofSeconds(config.getConnectTimeoutSeconds());

        return McpClient.sync(transport)
            .requestTimeout(connectTimeout)
            .build();
    }

    /**
     * 构建安全的子进程环境变量。
     * <p>
     * 仅传递 PATH/HOME/USER 等安全基线变量和用户显式指定的变量，
     * 防止意外泄露 API 密钥等敏感信息到子进程。
     */
    static Map<String, String> buildSafeEnv(Map<String, String> userEnv) {
        Map<String, String> env = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (SAFE_ENV_KEYS.contains(entry.getKey()) || entry.getKey().startsWith("XDG_")) {
                env.put(entry.getKey(), entry.getValue());
            }
        }
        if (userEnv != null) {
            env.putAll(userEnv);
        }
        return env;
    }

    private List<String> listToolNames(McpSyncClient client) {
        try {
            McpSchema.ListToolsResult result = client.listTools();
            if (result == null || result.tools() == null) {
                return List.of();
            }
            return result.tools().stream()
                .map(McpSchema.Tool::name)
                .toList();
        } catch (Exception e) {
            log.warn("Failed to list tools: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 服务器状态快照。
     */
    public record ServerStatus(
        String name,
        McpConnectionState state,
        String command,
        List<String> args,
        int toolCount,
        List<String> toolNames,
        Instant lastConnectedAt,
        String lastError,
        boolean enabled
    ) {}
}
