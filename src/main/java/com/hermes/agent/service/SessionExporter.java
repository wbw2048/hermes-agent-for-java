package com.hermes.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hermes.agent.entity.SessionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话导出服务，支持 JSON 和 Markdown 格式。
 */
@Service
public class SessionExporter {

    private static final Logger log = LoggerFactory.getLogger(SessionExporter.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final SessionStorageService sessionStorageService;
    private final ObjectMapper jsonMapper;

    public SessionExporter(SessionStorageService sessionStorageService) {
        this.sessionStorageService = sessionStorageService;
        this.jsonMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    /**
     * 导出会话为 JSON 格式。
     */
    public String exportJson(String sessionId) {
        SessionEntity session = sessionStorageService.getSession(sessionId);
        if (session == null) throw new IllegalArgumentException("Session not found: " + sessionId);

        List<Message> messages = sessionStorageService.loadSession(sessionId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId());
        data.put("title", session.getTitle());
        data.put("createdAt", session.getCreatedAt().toString());
        data.put("updatedAt", session.getUpdatedAt().toString());
        data.put("messageCount", session.getMessageCount());

        List<Map<String, Object>> msgList = new ArrayList<>();
        for (Message msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.getMessageType().name().toLowerCase());
            m.put("content", msg.getText());
            if (msg instanceof AssistantMessage am && !am.getToolCalls().isEmpty()) {
                m.put("toolCalls", am.getToolCalls());
            }
            msgList.add(m);
        }
        data.put("messages", msgList);

        try {
            return jsonMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON export failed", e);
        }
    }

    /**
     * 导出会话为 Markdown 格式。
     */
    public String exportMarkdown(String sessionId) {
        SessionEntity session = sessionStorageService.getSession(sessionId);
        if (session == null) throw new IllegalArgumentException("Session not found: " + sessionId);

        List<Message> messages = sessionStorageService.loadSession(sessionId);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(session.getTitle() != null ? session.getTitle() : "Untitled").append("\n\n");
        sb.append("- Session ID: ").append(session.getId()).append("\n");
        sb.append("- Created: ").append(FMT.format(session.getCreatedAt())).append("\n");
        sb.append("- Updated: ").append(FMT.format(session.getUpdatedAt())).append("\n");
        sb.append("- Messages: ").append(session.getMessageCount()).append("\n\n");
        sb.append("---\n\n");

        for (Message msg : messages) {
            String roleLabel = switch (msg.getMessageType()) {
                case USER -> "User";
                case ASSISTANT -> "Assistant";
                case TOOL -> "Tool Result";
                case SYSTEM -> "System";
            };
            sb.append("## ").append(roleLabel).append("\n\n");
            sb.append(msg.getText() != null ? msg.getText() : "(empty)").append("\n\n");

            if (msg instanceof AssistantMessage am && !am.getToolCalls().isEmpty()) {
                sb.append("```json\n");
                try {
                    sb.append(jsonMapper.writeValueAsString(am.getToolCalls()));
                } catch (JsonProcessingException e) {
                    sb.append("(tool calls serialization failed)");
                }
                sb.append("\n```\n\n");
            }
            if (msg instanceof ToolResponseMessage trm) {
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    sb.append("**Tool: ").append(tr.name()).append("**\n\n");
                    sb.append(tr.responseData() != null ? tr.responseData() : "(empty)").append("\n\n");
                }
            }
            sb.append("---\n\n");
        }

        return sb.toString();
    }
}
