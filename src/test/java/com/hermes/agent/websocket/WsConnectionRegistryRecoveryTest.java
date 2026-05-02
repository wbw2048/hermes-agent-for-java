package com.hermes.agent.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WsConnectionRegistryRecoveryTest {

    private WsConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WsConnectionRegistry();
    }

    @Test
    void markAndClearPendingConversation() {
        registry.markConversationInProgress("session-1", "Hello world");

        assertTrue(registry.getPendingMessage("session-1").isPresent());
        assertEquals("Hello world", registry.getPendingMessage("session-1").get());

        registry.clearPendingConversation("session-1");

        assertTrue(registry.getPendingMessage("session-1").isEmpty());
    }

    @Test
    void recordDisconnectRemovesPending() {
        registry.markConversationInProgress("session-1", "pending message");
        registry.recordDisconnect("session-1");

        assertTrue(registry.getPendingMessage("session-1").isEmpty());
    }

    @Test
    void isRecentDisconnectReturnsTrueWithinWindow() {
        // WsConnectionRegistry uses 5-minute window
        registry.recordDisconnect("session-1");

        assertTrue(registry.isRecentDisconnect("session-1"));
    }

    @Test
    void isRecentDisconnectReturnsFalseForUnknown() {
        assertFalse(registry.isRecentDisconnect("nonexistent"));
    }

    @Test
    void getPendingMessageReturnsEmptyForUnknown() {
        assertTrue(registry.getPendingMessage("nonexistent").isEmpty());
    }

    @Test
    void registerAndUnregisterWorkWithPendingConversations() {
        registry.markConversationInProgress("session-1", "test message");
        assertTrue(registry.getPendingMessage("session-1").isPresent());

        registry.clearPendingConversation("session-1");
        assertTrue(registry.getPendingMessage("session-1").isEmpty());

        // No exception on double-clear
        registry.clearPendingConversation("session-1");
    }
}
