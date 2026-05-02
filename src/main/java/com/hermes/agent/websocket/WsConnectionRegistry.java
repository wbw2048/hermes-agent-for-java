package com.hermes.agent.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接注册表。
 * <p>
 * 维护 sessionId -> WebSocketSession 的映射，用于向指定客户端推送消息。
 */
@Component
public class WsConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsConnectionRegistry.class);

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** 注册连接 */
    public void register(String sessionId, WebSocketSession wsSession) {
        sessions.put(sessionId, wsSession);
        log.info("WebSocket registered: sessionId={}, total={}", sessionId, sessions.size());
    }

    /** 注销连接 */
    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        log.info("WebSocket unregistered: sessionId={}, total={}", sessionId, sessions.size());
    }

    /** 获取指定会话的连接 */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /** 检查会话是否已连接 */
    public boolean isConnected(String sessionId) {
        WebSocketSession ws = sessions.get(sessionId);
        return ws != null && ws.isOpen();
    }

    /** 当前连接数 */
    public int size() {
        return sessions.size();
    }
}
