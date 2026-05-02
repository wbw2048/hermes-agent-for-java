package com.hermes.agent.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WebSocket 连接注册表测试。
 */
class WsConnectionRegistryTest {

    private WsConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WsConnectionRegistry();
    }

    @Test
    void registerAndGetSession() {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        when(mockSession.isOpen()).thenReturn(true);
        registry.register("session-1", mockSession);

        assertEquals(mockSession, registry.getSession("session-1"));
        assertTrue(registry.isConnected("session-1"));
        assertEquals(1, registry.size());
    }

    @Test
    void unregisterRemovesSession() {
        WebSocketSession mockSession = mock(WebSocketSession.class);
        registry.register("session-1", mockSession);
        assertEquals(1, registry.size());

        registry.unregister("session-1");
        assertNull(registry.getSession("session-1"));
        assertFalse(registry.isConnected("session-1"));
        assertEquals(0, registry.size());
    }

    @Test
    void unregisterNonExistentSession() {
        registry.unregister("nonexistent");
        assertEquals(0, registry.size());
    }

    @Test
    void isConnectedReturnsFalseForUnknownSession() {
        assertFalse(registry.isConnected("unknown"));
    }

    @Test
    void registerMultipleSessions() {
        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);
        WebSocketSession s3 = mock(WebSocketSession.class);
        when(s1.isOpen()).thenReturn(true);
        when(s2.isOpen()).thenReturn(true);
        when(s3.isOpen()).thenReturn(true);

        registry.register("s1", s1);
        registry.register("s2", s2);
        registry.register("s3", s3);

        assertEquals(3, registry.size());
        assertTrue(registry.isConnected("s1"));
        assertTrue(registry.isConnected("s2"));
        assertTrue(registry.isConnected("s3"));
    }

    @Test
    void registerOverwritesExistingSession() {
        WebSocketSession oldSession = mock(WebSocketSession.class);
        WebSocketSession newSession = mock(WebSocketSession.class);

        registry.register("s1", oldSession);
        registry.register("s1", newSession);

        assertEquals(newSession, registry.getSession("s1"));
        assertEquals(1, registry.size());
    }
}
