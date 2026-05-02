package com.hermes.agent.controller;

import com.hermes.agent.agent.SimpleAgent;
import com.hermes.agent.entity.SessionEntity;
import com.hermes.agent.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话 REST 控制器。
 * <p>
 * 提供发送消息、获取历史、会话管理和健康检查等接口。
 */
@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "*")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final SimpleAgent simpleAgent;
    private final MemoryStore memoryStore;

    public ConversationController(@Lazy SimpleAgent simpleAgent, MemoryStore memoryStore) {
        this.simpleAgent = simpleAgent;
        this.memoryStore = memoryStore;
    }

    /**
     * 处理对话请求。
     * 未提供会话 ID 时自动生成 UUID。
     *
     * @param request 对话请求，包含消息和可选的会话 ID
     * @return 智能体响应及会话 ID
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> handleConversation(@RequestBody ConversationRequest request) {
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                ? request.sessionId()
                : UUID.randomUUID().toString();

        log.info("Received conversation request: sessionId={}, messageLength={}",
                sessionId, request.message().length());

        try {
            String response = simpleAgent.runConversation(sessionId, request.message());

            List<ToolCallInfo> toolCalls = simpleAgent.getToolCallTracker().getCalls();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", response);
            result.put("sessionId", sessionId);
            result.put("toolCalls", toolCalls);

            log.info("Conversation completed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error processing conversation request", e);

            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("sessionId", sessionId);

            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 列出所有会话，按更新时间倒序。
     * GET /api/conversations
     */
    @GetMapping
    public ResponseEntity<List<SessionEntity>> listSessions() {
        List<SessionEntity> sessions = simpleAgent.getSessionStorageService().listSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * 创建空会话，返回新会话实体。
     * POST /api/conversations/session
     */
    @PostMapping("/session")
    public ResponseEntity<SessionEntity> createSession() {
        String sessionId = UUID.randomUUID().toString();
        simpleAgent.getSessionStorageService().createSession(sessionId, null);
        SessionEntity session = simpleAgent.getSessionStorageService().getSession(sessionId);
        return ResponseEntity.ok(session);
    }

    /**
     * 健康检查端点，返回服务状态和可用工具列表。
     * GET /api/conversations/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "hermes-agent");
        response.put("version", "0.1.0-SNAPSHOT");
        response.put("tools", String.join(", ", simpleAgent.getAvailableTools()));
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定会话的对话历史。
     * GET /api/conversations/{sessionId}/history
     */
    @GetMapping("/{sessionId}/history")
    public ResponseEntity<List<Message>> getHistory(@PathVariable String sessionId) {
        return ResponseEntity.ok(simpleAgent.getConversationHistory(sessionId));
    }

    /**
     * 清除指定会话的全部对话历史。
     * DELETE /api/conversations/{sessionId}/history
     */
    @DeleteMapping("/{sessionId}/history")
    public ResponseEntity<Map<String, String>> clearHistory(@PathVariable String sessionId) {
        simpleAgent.clearHistory(sessionId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "对话历史已清除");
        return ResponseEntity.ok(response);
    }

    /**
     * 删除整个会话（包括会话元数据）。
     * DELETE /api/conversations/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        simpleAgent.getSessionStorageService().deleteSession(sessionId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "会话已删除");
        return ResponseEntity.ok(response);
    }

    /**
     * 更新会话标题。
     * POST /api/conversations/{sessionId}/title
     */
    @PostMapping("/{sessionId}/title")
    public ResponseEntity<Map<String, String>> updateTitle(
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request
    ) {
        String title = request.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title is required"));
        }

        SessionEntity session = simpleAgent.getSessionStorageService().getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        simpleAgent.getSessionStorageService().updateSessionTitle(sessionId, title);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "会话标题已更新");
        return ResponseEntity.ok(response);
    }

    /**
     * 手动触发上下文压缩。
     * POST /api/conversations/{sessionId}/compress
     */
    @PostMapping("/{sessionId}/compress")
    public ResponseEntity<Map<String, Object>> compress(@PathVariable String sessionId) {
        log.info("Manual compression requested for session: {}", sessionId);

        SessionEntity session = simpleAgent.getSessionStorageService().getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }

        int before = simpleAgent.getConversationHistory(sessionId).size();
        int after = simpleAgent.compressContext(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("messagesBefore", before);
        result.put("messagesAfter", after);
        result.put("messagesRemoved", before - after);

        return ResponseEntity.ok(result);
    }

    /**
     * 流式对话端点，使用 SSE 推送事件。
     * POST /api/conversations/stream
     * <p>
     * 事件类型：
     * - {@code tool}: 工具调用详情（名称、参数、结果、耗时）
     * - {@code text}: 文本响应片段（逐 token 推送）
     * - {@code done}: 流式完成
     * - {@code error}: 错误信息
     */
    @PostMapping("/stream")
    public SseEmitter streamConversation(@RequestBody ConversationRequest request) {
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                ? request.sessionId()
                : UUID.randomUUID().toString();

        log.info("Received stream request: sessionId={}, messageLength={}", sessionId, request.message().length());

        // 超时设为 5 分钟
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);

        // 超时回调
        emitter.onTimeout(() -> {
            log.warn(">>> [STREAM-TIMEOUT] sessionId={}", sessionId);
            try {
                emitter.send(SseEmitter.event().name("error").data("请求超时"));
            } catch (IOException ignored) {}
            emitter.complete();
        });

        // 断开连接回调
        emitter.onCompletion(() -> {
            log.info(">>> [STREAM-COMPLETED] sessionId={}", sessionId);
        });

        simpleAgent.streamConversation(sessionId, request.message(), emitter);

        return emitter;
    }

    // ==================== 记忆管理 API ====================

    /**
     * 查看当前长期记忆条目。
     * GET /api/memory
     */
    @GetMapping("/memory")
    public ResponseEntity<Map<String, Object>> getMemory() {
        Map<String, List<String>> entries = memoryStore.getAllEntries();
        return ResponseEntity.ok(Map.of("success", true, "entries", entries));
    }

    /**
     * 手动添加记忆。
     * POST /api/memory/{target}
     * Body: { "content": "条目内容" }
     */
    @PostMapping("/memory/{target}")
    public ResponseEntity<Map<String, Object>> addMemory(
            @PathVariable String target,
            @RequestBody Map<String, String> request
    ) {
        String content = request.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "content is required"));
        }
        if (!"memory".equals(target) && !"user".equals(target)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "target must be 'memory' or 'user'"));
        }
        Map<String, Object> result = memoryStore.add(target, content);
        return ResponseEntity.ok(result);
    }

    /**
     * 清除指定目标的所有记忆。
     * DELETE /api/memory/{target}
     */
    @DeleteMapping("/memory/{target}")
    public ResponseEntity<Map<String, String>> clearMemory(@PathVariable String target) {
        if (!"memory".equals(target) && !"user".equals(target)) {
            return ResponseEntity.badRequest().body(Map.of("error", "target must be 'memory' or 'user'"));
        }
        memoryStore.clear(target);
        return ResponseEntity.ok(Map.of("status", "success", "message", target + " memory cleared"));
    }

    /**
     * 清除所有记忆（memory + user）。
     * DELETE /api/memory
     */
    @DeleteMapping("/memory")
    public ResponseEntity<Map<String, String>> clearAllMemory() {
        memoryStore.clear("memory");
        memoryStore.clear("user");
        return ResponseEntity.ok(Map.of("status", "success", "message", "all memory cleared"));
    }
}
