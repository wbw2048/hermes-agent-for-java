package com.hermes.agent.controller;

import com.hermes.agent.config.McpServerProperties;
import com.hermes.agent.config.McpServerProperties.ServerConfig;
import com.hermes.agent.mcp.McpConnectionManager;
import com.hermes.agent.mcp.McpConnectionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpControllerTest {

    @Mock
    private McpConnectionManager connectionManager;

    private McpServerProperties properties;
    private McpController controller;

    @BeforeEach
    void setUp() {
        properties = new McpServerProperties();
        ServerConfig config = new ServerConfig();
        config.setCommand("npx");
        config.setArgs(List.of("-y", "test"));
        config.setEnabled(true);
        properties.getServers().put("test-server", config);

        controller = new McpController(connectionManager, properties);
    }

    @Test
    void listServersReturnsAllConfiguredServers() {
        when(connectionManager.getState("test-server")).thenReturn(McpConnectionState.DISCONNECTED);
        when(connectionManager.getDiscoveredTools("test-server")).thenReturn(List.of());

        Map<String, Object> result = controller.listServers();
        assertTrue((Boolean) result.get("success"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> servers = (List<Map<String, Object>>) result.get("servers");
        assertEquals(1, servers.size());
        assertEquals("test-server", servers.get(0).get("name"));
    }

    @Test
    void connectServerReturnsNotFoundForUnknownServer() {
        var response = controller.connectServer("unknown");
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void disconnectServerWorks() {
        var response = controller.disconnectServer("test-server");
        assertEquals(200, response.getStatusCode().value());
        verify(connectionManager).disconnect("test-server");
    }

    @Test
    void reconnectServerWorks() {
        var response = controller.reconnectServer("test-server");
        assertEquals(200, response.getStatusCode().value());
        verify(connectionManager).reconnect("test-server");
    }

    @Test
    void healthCheckReturnsState() {
        when(connectionManager.getState("test-server")).thenReturn(McpConnectionState.CONNECTED);
        when(connectionManager.getLastConnectedAt("test-server")).thenReturn(null);
        when(connectionManager.getLastError("test-server")).thenReturn(null);

        Map<String, Object> result = controller.healthCheck("test-server");
        assertTrue((Boolean) result.get("success"));
        assertEquals("CONNECTED", result.get("state"));
        assertEquals(true, result.get("healthy"));
    }
}
