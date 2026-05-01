package com.hermes.agent.controller;

import com.hermes.agent.agent.SimpleAgent;
import org.springframework.ai.chat.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话 REST 控制器。
 * <p>
 * 提供发送消息、获取历史、清除会话和健康检查等接口。
 */
@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "*")
public class ConversationController {

    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    private final SimpleAgent simpleAgent;

    public ConversationController(SimpleAgent simpleAgent) {
        this.simpleAgent = simpleAgent;
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

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", response);
            result.put("sessionId", sessionId);

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
}
