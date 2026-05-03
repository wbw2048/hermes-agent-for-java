package com.hermes.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.MessageEntity;
import com.hermes.agent.entity.SessionEntity;
import com.hermes.agent.repository.MessageRepository;
import com.hermes.agent.repository.SessionRepository;
import com.hermes.agent.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 会话存储服务，负责会话和消息的持久化。
 */
@Service
public class SessionStorageService {

    private static final Logger log = LoggerFactory.getLogger(SessionStorageService.class);

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final WorkspaceManager workspaceManager;

    public SessionStorageService(SessionRepository sessionRepository,
                                 MessageRepository messageRepository,
                                 ObjectMapper objectMapper,
                                 WorkspaceManager workspaceManager) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.workspaceManager = workspaceManager;
    }

    /**
     * 创建新会话。
     *
     * @param sessionId 会话唯一标识
     * @param title     会话标题（可空）
     */
    @Transactional
    public void createSession(String sessionId, String title) {
        if (sessionRepository.existsById(sessionId)) {
            log.debug("Session already exists: {}", sessionId);
            return;
        }
        Instant now = Instant.now();
        SessionEntity session = new SessionEntity(sessionId, title, now, now);
        // 初始化 workspace
        try {
            String workspacePath = workspaceManager.createWorkspace(sessionId).toString();
            session.setWorkspaceDir(workspacePath);
        } catch (Exception e) {
            log.warn("Failed to create workspace for session {}: {}", sessionId, e.getMessage());
        }
        sessionRepository.save(session);
        log.info("Created session: id={}, title={}", sessionId, title);
    }

    /**
     * 批量保存消息到指定会话。
     *
     * @param sessionId 会话 ID
     * @param messages  Spring AI Message 列表
     */
    @Transactional
    public void saveMessages(String sessionId, List<Message> messages) {
        long existingCount = messageRepository.countBySessionId(sessionId);
        List<MessageEntity> entities = new ArrayList<>();
        int orderIndex = (int) existingCount;

        for (Message msg : messages) {
            MessageEntity entity = toEntity(sessionId, msg, orderIndex++);
            entities.add(entity);
        }

        messageRepository.saveAll(entities);

        // 更新会话的消息计数和更新时间
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setMessageCount((int) messageRepository.countBySessionId(sessionId));
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
        });

        log.debug("Saved {} messages to session {}", messages.size(), sessionId);
    }

    /**
     * 从数据库加载指定会话的全部消息。
     *
     * @param sessionId 会话 ID
     * @return Spring AI Message 列表
     */
    @Transactional(readOnly = true)
    public List<Message> loadSession(String sessionId) {
        List<MessageEntity> entities = messageRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        List<Message> messages = new ArrayList<>();

        for (MessageEntity entity : entities) {
            Message msg = toMessage(entity);
            if (msg != null) {
                messages.add(msg);
            }
        }

        log.debug("Loaded {} messages from session {}", messages.size(), sessionId);
        return messages;
    }

    /**
     * 列出所有会话，按更新时间倒序。
     */
    @Transactional(readOnly = true)
    public List<SessionEntity> listSessions() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc();
    }

    /**
     * 获取单个会话。
     */
    @Transactional(readOnly = true)
    public SessionEntity getSession(String sessionId) {
        return sessionRepository.findById(sessionId).orElse(null);
    }

    /**
     * 删除指定会话及其全部消息。
     *
     * @param sessionId 会话 ID
     */
    @Transactional
    public void deleteSession(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        // 清理 workspace
        workspaceManager.deleteWorkspace(sessionId);
        log.info("Deleted session: {}", sessionId);
    }

    /**
     * 更新会话标题。
     */
    @Transactional
    public void updateSessionTitle(String sessionId, String title) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setTitle(title);
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
            log.info("Updated session title: {} -> {}", sessionId, title);
        });
    }

    // --- 内部转换方法 ---

    private MessageEntity toEntity(String sessionId, Message message, int orderIndex) {
        String role = message.getMessageType().name();
        String content = message.getText();
        String toolCallsJson = null;
        String toolCallId = null;

        if (message instanceof AssistantMessage assistant) {
            if (!assistant.getToolCalls().isEmpty()) {
                try {
                    toolCallsJson = objectMapper.writeValueAsString(assistant.getToolCalls());
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize tool calls: {}", e.getMessage());
                }
            }
        } else if (message instanceof ToolResponseMessage toolResp) {
            // 如果有多个 ToolResponse，取第一个的 id 作为 toolCallId
            if (!toolResp.getResponses().isEmpty()) {
                toolCallId = toolResp.getResponses().get(0).id();
                // 将全部 ToolResponse 序列化到 toolCalls 字段
                try {
                    toolCallsJson = objectMapper.writeValueAsString(toolResp.getResponses());
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize tool responses: {}", e.getMessage());
                }
            }
        }

        return new MessageEntity(sessionId, role, content, toolCallsJson, toolCallId, Instant.now(), orderIndex);
    }

    private Message toMessage(MessageEntity entity) {
        MessageType type;
        try {
            type = MessageType.valueOf(entity.getRole());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown message role: {}", entity.getRole());
            return null;
        }

        return switch (type) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> {
                List<ToolCall> toolCalls = parseToolCalls(entity.getToolCalls());
                yield AssistantMessage.builder()
                        .content(entity.getContent())
                        .toolCalls(toolCalls)
                        .build();
            }
            case TOOL -> {
                List<ToolResponseMessage.ToolResponse> responses = parseToolResponses(entity.getToolCalls());
                yield ToolResponseMessage.builder()
                        .responses(responses)
                        .build();
            }
            case SYSTEM -> new SystemMessage(entity.getContent());
        };
    }

    private List<ToolCall> parseToolCalls(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list.stream()
                    .map(m -> new ToolCall(m.get("id"), m.get("type"), m.get("name"), m.get("arguments")))
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize tool calls: {}", e.getMessage());
            return List.of();
        }
    }

    private List<ToolResponseMessage.ToolResponse> parseToolResponses(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, String>> list = objectMapper.readValue(json, new TypeReference<>() {});
            return list.stream()
                    .map(m -> new ToolResponseMessage.ToolResponse(m.get("id"), m.get("name"), m.get("responseData")))
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize tool responses: {}", e.getMessage());
            return List.of();
        }
    }
}
