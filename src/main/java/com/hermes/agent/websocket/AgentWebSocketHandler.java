package com.hermes.agent.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.agent.SimpleAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket 消息处理器。
 * <p>
 * 接收客户端 JSON 消息，路由到 SimpleAgent 进行流式对话，并向客户端推送事件。
 */
@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final SimpleAgent simpleAgent;
    private final WsConnectionRegistry registry;
    private final ObjectMapper objectMapper;

    public AgentWebSocketHandler(@Lazy SimpleAgent simpleAgent, WsConnectionRegistry registry, ObjectMapper objectMapper) {
        this.simpleAgent = simpleAgent;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connection established: id={}, remote={}", session.getId(), session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        log.info("Received WebSocket message: length={}", payload.length());

        try {
            WsMessage wsMsg = objectMapper.readValue(payload, WsMessage.class);

            if (!"chat".equals(wsMsg.type())) {
                send(session, WsMessage.error("不支持的消息类型: " + wsMsg.type()));
                return;
            }

            if (wsMsg.sessionId() == null || wsMsg.message() == null || wsMsg.message().isBlank()) {
                send(session, WsMessage.error("sessionId 和 message 不能为空"));
                return;
            }

            String sessionId = wsMsg.sessionId();
            registry.register(sessionId, session);
            simpleAgent.streamConversationWs(sessionId, wsMsg.message(), msg -> send(session, msg));

        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            safeSend(session, WsMessage.error("消息处理失败: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 清理所有绑定到此 WebSocketSession 的 sessionId
        registry.unregister(session.getId());
        log.info("WebSocket connection closed: id={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error: id={}", session.getId(), exception);
        registry.unregister(session.getId());
    }

    private void send(WebSocketSession session, WsMessage msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
        }
    }

    private void safeSend(WebSocketSession session, WsMessage msg) {
        if (session.isOpen()) {
            send(session, msg);
        }
    }
}
