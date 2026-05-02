package com.hermes.agent.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

    /** 正在进行的对话：sessionId -> 最后正在处理的用户消息 */
    private final Map<String, String> pendingConversations = new ConcurrentHashMap<>();

    /** 断开连接时间戳：sessionId -> Instant */
    private final Map<String, Instant> disconnectTimestamps = new ConcurrentHashMap<>();

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

    /** 标记指定会话的对话正在进行 */
    public void markConversationInProgress(String sessionId, String userMessage) {
        pendingConversations.put(sessionId, userMessage);
        disconnectTimestamps.remove(sessionId);
    }

    /** 清除指定会话的待处理对话 */
    public void clearPendingConversation(String sessionId) {
        pendingConversations.remove(sessionId);
    }

    /** 获取正在进行的对话的用户消息 */
    public Optional<String> getPendingMessage(String sessionId) {
        return Optional.ofNullable(pendingConversations.get(sessionId));
    }

    /** 记录断开连接事件 */
    public void recordDisconnect(String sessionId) {
        disconnectTimestamps.put(sessionId, Instant.now());
        pendingConversations.remove(sessionId);
    }

    /** 检查是否为近期断开（5 分钟内） */
    public boolean isRecentDisconnect(String sessionId) {
        Instant disconnectTime = disconnectTimestamps.get(sessionId);
        if (disconnectTime == null) return false;
        return Instant.now().minusSeconds(300).isBefore(disconnectTime);
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
