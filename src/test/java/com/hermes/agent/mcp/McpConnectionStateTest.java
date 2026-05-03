package com.hermes.agent.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpConnectionStateTest {

    @Test
    void hasAllExpectedStates() {
        assertEquals(5, McpConnectionState.values().length);
        assertNotNull(McpConnectionState.DISCONNECTED);
        assertNotNull(McpConnectionState.CONNECTING);
        assertNotNull(McpConnectionState.CONNECTED);
        assertNotNull(McpConnectionState.ERROR);
        assertNotNull(McpConnectionState.SHUTDOWN);
    }

    @Test
    void valueOfWorks() {
        assertEquals(McpConnectionState.CONNECTED, McpConnectionState.valueOf("CONNECTED"));
        assertEquals(McpConnectionState.DISCONNECTED, McpConnectionState.valueOf("DISCONNECTED"));
        assertEquals(McpConnectionState.ERROR, McpConnectionState.valueOf("ERROR"));
    }
}
