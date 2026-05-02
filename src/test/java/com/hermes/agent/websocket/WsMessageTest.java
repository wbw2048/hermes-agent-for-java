package com.hermes.agent.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 消息协议测试。
 */
class WsMessageTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void chatMessage() {
        WsMessage msg = WsMessage.chat("session-1", "你好");
        assertEquals("chat", msg.type());
        assertEquals("session-1", msg.sessionId());
        assertEquals("你好", msg.message());
        assertNull(msg.data());
    }

    @Test
    void textMessage() {
        WsMessage msg = WsMessage.text("你好世界");
        assertEquals("text", msg.type());
        assertEquals("你好世界", msg.data());
        assertNull(msg.sessionId());
    }

    @Test
    void toolCallMessage() {
        WsMessage msg = WsMessage.toolCall("get_time", "{\"timezone\": \"Asia/Shanghai\"}");
        assertEquals("tool_call", msg.type());
        assertEquals("get_time", msg.toolName());
        assertEquals("{\"timezone\": \"Asia/Shanghai\"}", msg.arguments());
    }

    @Test
    void toolResultMessage() {
        WsMessage msg = WsMessage.toolResult("get_time", "2026-05-02 14:30:00", 15);
        assertEquals("tool_result", msg.type());
        assertEquals("get_time", msg.toolName());
        assertEquals("2026-05-02 14:30:00", msg.result());
        assertEquals(15L, msg.elapsedMs());
    }

    @Test
    void doneMessage() {
        WsMessage msg = WsMessage.done();
        assertEquals("done", msg.type());
        assertNull(msg.data());
        assertNull(msg.sessionId());
    }

    @Test
    void errorMessage() {
        WsMessage msg = WsMessage.error("连接超时");
        assertEquals("error", msg.type());
        assertEquals("连接超时", msg.data());
    }

    @Test
    void chatMessageJsonSerialization() throws Exception {
        WsMessage msg = WsMessage.chat("s1", "测试消息");
        String json = objectMapper.writeValueAsString(msg);

        WsMessage parsed = objectMapper.readValue(json, WsMessage.class);
        assertEquals("chat", parsed.type());
        assertEquals("s1", parsed.sessionId());
        assertEquals("测试消息", parsed.message());
    }

    @Test
    void textMessageJsonSerialization() throws Exception {
        WsMessage msg = WsMessage.text("流式文本");
        String json = objectMapper.writeValueAsString(msg);

        WsMessage parsed = objectMapper.readValue(json, WsMessage.class);
        assertEquals("text", parsed.type());
        assertEquals("流式文本", parsed.data());
    }

    @Test
    void unknownMessageType() throws Exception {
        String json = "{\"type\":\"unknown_type\"}";
        WsMessage msg = objectMapper.readValue(json, WsMessage.class);
        assertEquals("unknown_type", msg.type());
    }
}
