package com.hermes.agent.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationRequestTest {

    @Test
    void constructorThrowsOnNullMessage() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationRequest(null, "session"));
    }

    @Test
    void constructorThrowsOnBlankMessage() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationRequest("   ", "session"));
    }

    @Test
    void constructorThrowsOnEmptyMessage() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationRequest("", "session"));
    }

    @Test
    void constructorSetsEmptySessionIdWhenNull() {
        ConversationRequest req = new ConversationRequest("hello", null);
        assertEquals("hello", req.message());
        assertEquals("", req.sessionId());
    }

    @Test
    void constructorPreservesSessionId() {
        ConversationRequest req = new ConversationRequest("hello", "abc-123");
        assertEquals("hello", req.message());
        assertEquals("abc-123", req.sessionId());
    }

    @Test
    void acceptsNormalMessage() {
        ConversationRequest req = new ConversationRequest("What time is it?", "s1");
        assertEquals("What time is it?", req.message());
    }
}
