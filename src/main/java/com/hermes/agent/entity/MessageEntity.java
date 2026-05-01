package com.hermes.agent.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 消息实体，映射 messages 表。
 */
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_messages_session_order", columnList = "session_id, order_index")
})
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;

    @Column(name = "tool_call_id")
    private String toolCallId;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    public MessageEntity() {}

    public MessageEntity(String sessionId, String role, String content, String toolCalls,
                         String toolCallId, Instant timestamp, int orderIndex) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
        this.timestamp = timestamp;
        this.orderIndex = orderIndex;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCalls() { return toolCalls; }
    public void setToolCalls(String toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
