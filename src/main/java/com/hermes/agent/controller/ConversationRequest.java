package com.hermes.agent.controller;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对话请求 DTO。
 * <p>
 * 携带用户消息内容和可选的会话 ID。未提供会话 ID 时由控制器生成。
 */
public record ConversationRequest(
        @JsonProperty("message") String message,
        @JsonProperty("sessionId") String sessionId
) {
    /**
     * 验证型规范构造器。确保消息不为空，将会话 ID 缺失时规范化为空字符串。
     */
    public ConversationRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be null or blank");
        }
        if (sessionId == null) {
            sessionId = "";
        }
    }
}