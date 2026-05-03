package com.hermes.agent.mcp;

import com.hermes.agent.config.McpServerProperties;
import com.hermes.agent.config.McpServerProperties.ServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpConnectionManagerTest {

    private McpConnectionManager manager;
    private McpServerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new McpServerProperties();
        manager = new McpConnectionManager(properties);
    }

    @Test
    void initialStatesAreDisconnected() {
        ServerConfig config = new ServerConfig();
        config.setCommand("npx");
        config.setEnabled(false);
        properties.getServers().put("test", config);

        assertEquals(McpConnectionState.DISCONNECTED, manager.getState("test"));
    }

    @Test
    void unknownServerStateIsDisconnected() {
        assertEquals(McpConnectionState.DISCONNECTED, manager.getState("nonexistent"));
    }

    @Test
    void getAllStatusesReturnsAllConfiguredServers() {
        ServerConfig c1 = new ServerConfig();
        c1.setCommand("cmd1");
        c1.setEnabled(false);
        properties.getServers().put("server1", c1);

        ServerConfig c2 = new ServerConfig();
        c2.setCommand("cmd2");
        c2.setEnabled(false);
        properties.getServers().put("server2", c2);

        Map<String, McpConnectionManager.ServerStatus> statuses = manager.getAllStatuses();
        assertEquals(2, statuses.size());
        assertTrue(statuses.containsKey("server1"));
        assertTrue(statuses.containsKey("server2"));
    }

    @Test
    void disconnectNonExistentServerDoesNotThrow() {
        assertDoesNotThrow(() -> manager.disconnect("nonexistent"));
    }

    @Test
    void buildSafeEnvFiltersCorrectly() {
        // buildSafeEnv is static and uses System.getenv() + userEnv
        Map<String, String> userEnv = Map.of("MY_CUSTOM", "value");
        Map<String, String> result = McpConnectionManager.buildSafeEnv(userEnv);

        // Should contain PATH (always safe)
        assertTrue(result.containsKey("PATH"));
        // Should contain user-specified env
        assertEquals("value", result.get("MY_CUSTOM"));
    }

    @Test
    void connectThrowsOnMissingCommand() {
        ServerConfig config = new ServerConfig();
        config.setCommand("");
        config.setEnabled(true);
        properties.getServers().put("empty", config);

        assertThrows(IllegalArgumentException.class, () -> manager.connect("empty"));
    }

    @Test
    void connectThrowsOnUnknownServer() {
        assertThrows(IllegalArgumentException.class, () -> manager.connect("unknown"));
    }
}
