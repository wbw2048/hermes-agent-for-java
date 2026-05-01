package com.hermes.agent.agent;

import com.hermes.agent.compressor.ContextCompressor;
import com.hermes.agent.compressor.TokenEstimator;
import com.hermes.agent.config.ContextCompressionProperties;
import com.hermes.agent.controller.ToolCallInfo;
import com.hermes.agent.controller.ToolCallTracker;
import com.hermes.agent.prompt.PromptBuilder;
import com.hermes.agent.service.SessionStorageService;
import com.hermes.agent.tool.ToolSetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 带工具调用功能的 AI 智能体。
 * <p>
 * 将用户消息追加到会话历史后，调用 LLM 并让 Spring AI 自动处理工具调用循环，
 * 最终返回助手响应。工具调用由 Spring AI 的 ToolCallingManager 自动管理。
 * <p>
 * 使用 SQLite 持久化会话历史。
 */
@Component
public class SimpleAgent {

    private static final Logger log = LoggerFactory.getLogger(SimpleAgent.class);

    private final ChatClient chatClient;
    private final Object[] toolObjects;
    private final PromptBuilder promptBuilder;
    private final ContextCompressor contextCompressor;
    private final TokenEstimator tokenEstimator;
    private final ContextCompressionProperties compressionProperties;
    private final SessionStorageService sessionStorageService;
    private final ToolCallTracker toolCallTracker;

    /**
     * 创建智能体实例。
     *
     * @param chatClientBuilder      Spring AI ChatClient 构建器
     * @param toolSetManager         工具集管理器，负责按配置过滤工具 Bean
     * @param allToolBeans           所有工具 Bean
     * @param sessionStorageService  会话存储服务
     * @param promptBuilder          系统提示构建器
     * @param contextCompressor      上下文压缩器
     * @param tokenEstimator         令牌估算器
     * @param compressionProperties  压缩配置
     * @param toolCallTracker        工具调用追踪器
     * @param defaultSystemPrompt    系统提示词（向后兼容，若 PromptBuilder 未启用则使用）
     */
    public SimpleAgent(
            ChatClient.Builder chatClientBuilder,
            ToolSetManager toolSetManager,
            List<Object> allToolBeans,
            SessionStorageService sessionStorageService,
            PromptBuilder promptBuilder,
            ContextCompressor contextCompressor,
            TokenEstimator tokenEstimator,
            ContextCompressionProperties compressionProperties,
            ToolCallTracker toolCallTracker,
            @Value("${hermes.agent.default-system-prompt:你是一个有帮助的AI助手。请用中文回答问题。}")
            String defaultSystemPrompt
    ) {
        this.chatClient = chatClientBuilder.build();
        this.sessionStorageService = sessionStorageService;
        this.promptBuilder = promptBuilder;
        this.contextCompressor = contextCompressor;
        this.tokenEstimator = tokenEstimator;
        this.compressionProperties = compressionProperties;
        this.toolCallTracker = toolCallTracker;
        List<Object> activeBeans = toolSetManager.getActiveToolBeans(allToolBeans);
        this.toolObjects = wrapToolsForTracking(activeBeans);
        log.info("SimpleAgent initialized with {} tool beans (toolsets={}): {}",
            this.toolObjects.length, toolSetManager.getActiveToolSetNames(), getAvailableTools());
    }

    /**
     * 过滤出仅包含 @Tool 方法的 Bean，并包装追踪代理。
     */
    private Object[] wrapToolsForTracking(List<Object> beans) {
        return beans.stream()
            .filter(bean -> {
                for (Method m : bean.getClass().getDeclaredMethods()) {
                    if (m.isAnnotationPresent(Tool.class)) return true;
                }
                return false;
            })
            .map(toolCallTracker::wrap)
            .toArray();
    }

    /**
     * 执行对话流程。
     * 将用户消息追加到会话历史后，执行工具循环，最终返回助手响应。
     *
     * @param sessionId   会话唯一标识
     * @param userMessage 用户输入消息
     * @return 工具调用完成后助手的最终回复
     */
    public String runConversation(String sessionId, String userMessage) {
        log.info(">>> [LLM-REQUEST] sessionId={}", sessionId);

        // 确保会话存在
        sessionStorageService.createSession(sessionId, null);

        // 从数据库加载历史
        List<Message> messages = new ArrayList<>(sessionStorageService.loadSession(sessionId));

        // 追加用户消息
        messages.add(new UserMessage(userMessage));

        // 构建系统提示
        String systemPrompt = promptBuilder.buildSystemPrompt();
        log.info(">>> [LLM-SYSTEM] {}", systemPrompt.replaceAll("\\n", "\\\\n"));

        // 检查是否需要上下文压缩
        int estimatedTokens = tokenEstimator.estimateAll(messages);
        log.info(">>> [LLM-MESSAGES] sessionId={}, total={}, estimatedTokens={}",
            sessionId, messages.size(), estimatedTokens);

        if (contextCompressor.shouldCompress(estimatedTokens)) {
            log.info(">>> [CONTEXT-COMPRESSION] Triggering compression for sessionId={}", sessionId);
            messages = contextCompressor.compress(messages, compressionProperties.getContextLength());
            log.info(">>> [CONTEXT-COMPRESSION] After compression: {} messages", messages.size());
        }

        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            log.info("  [{}] type={} text={}", i, m.getMessageType(), m.getText());
        }

        // 记录本轮前的消息数量，用于后续保存新增消息
        int historySizeBefore = messages.size();

        try {
            toolCallTracker.startTracking();

            var chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .tools(toolObjects)
                .call()
                .chatResponse();

            var assistantMsg = chatResponse.getResult().getOutput();
            String content = assistantMsg.getText();

            log.info(">>> [LLM-RESPONSE] sessionId={}, responseText={}", sessionId, content != null ? content.replaceAll("\\n", "\\\\n") : "(null)");
            log.info(">>> [LLM-RESPONSE-DETAILS] messageType={}, toolCalls={}",
                assistantMsg.getMessageType(), assistantMsg.getToolCalls());

            // 保存本轮新增的所有消息（用户消息 + 中间 ToolResponseMessage + 最终助手消息）
            messages.add(assistantMsg);
            List<Message> newMessages = messages.subList(historySizeBefore, messages.size());
            sessionStorageService.saveMessages(sessionId, new ArrayList<>(newMessages));

            return content != null ? content : "";
        } catch (Exception e) {
            log.error(">>> [LLM-ERROR] sessionId={}: {}", sessionId, e.getMessage(), e);
            return "抱歉，处理您的请求时出现了错误: " + e.getMessage();
        } finally {
            toolCallTracker.stopTracking();
        }
    }

    /**
     * 流式对话。先完成工具调用循环，再流式输出最终文本。
     * <p>
     * 两阶段方案：
     * 1. {@code .call()} 执行完整的工具调用循环（Spring AI 自动处理），收集工具调用详情
     * 2. {@code .stream()} 复用已包含工具结果的消息历史，流式输出最终文本
     *
     * @param sessionId 会话标识
     * @param message   用户消息
     * @param emitter   SSE 发射器
     */
    public void streamConversation(String sessionId, String message, SseEmitter emitter) {
        log.info(">>> [STREAM-REQUEST] sessionId={}", sessionId);

        sessionStorageService.createSession(sessionId, null);
        List<Message> messages = new ArrayList<>(sessionStorageService.loadSession(sessionId));

        String systemPrompt = promptBuilder.buildSystemPrompt();

        // 先保存用户消息，确保即使后续流程出错也不会丢失
        UserMessage userMsg = new UserMessage(message);
        int historySizeBefore = messages.size();
        sessionStorageService.saveMessages(sessionId, List.of(userMsg));
        messages.add(userMsg);

        // 第一阶段：完成工具调用循环
        toolCallTracker.startTracking();
        try {
            ChatResponse toolResponse = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .tools(toolObjects)
                .call()
                .chatResponse();

            messages.add(toolResponse.getResult().getOutput());
        } catch (Exception e) {
            log.error(">>> [STREAM-ERROR] tool loop failed: {}", e.getMessage(), e);
            try { emitter.send(SseEmitter.event().name("error").data("工具调用失败: " + e.getMessage())); }
            catch (IOException ignored) {}
            emitter.complete();
            toolCallTracker.stopTracking();
            return;
        } finally {
            toolCallTracker.stopTracking();
        }

        // 发送工具调用事件
        for (ToolCallInfo tc : toolCallTracker.getCalls()) {
            try {
                emitter.send(SseEmitter.event().name("tool").data(tc));
            } catch (IOException e) {
                log.warn(">>> [STREAM] Failed to send tool event: {}", e.getMessage());
                emitter.complete();
                return;
            }
        }

        // 第二阶段：流式输出文本（不传 tools，历史已包含工具结果）
        try {
            final StringBuilder streamedText = new StringBuilder();
            final boolean[] saveCompleted = {false};

            Flux<ChatResponse> stream = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .stream()
                .chatResponse();

            stream.subscribe(
                response -> {
                    String text = response.getResult() != null ? response.getResult().getOutput().getText() : null;
                    if (text != null && !text.isEmpty()) {
                        streamedText.append(text);
                        try { emitter.send(SseEmitter.event().name("text").data(text)); }
                        catch (IOException e) { log.warn(">>> [STREAM] Failed to send text event: {}", e.getMessage()); }
                    }
                },
                error -> {
                    log.error(">>> [STREAM] Error: {}", error.getMessage(), error);
                    try { emitter.send(SseEmitter.event().name("error").data(error.getMessage())); }
                    catch (IOException e) {}
                    emitter.complete();
                },
                () -> {
                    // 流式完成后，保存本轮新增的消息（工具响应 + 流式助手响应）
                    try {
                        String finalText = streamedText.toString();

                        // 移除 .call() 阶段的助手消息，用 .stream() 的最终响应替代
                        int lastIdx = messages.size() - 1;
                        if (lastIdx >= 0 && messages.get(lastIdx).getMessageType() == MessageType.ASSISTANT) {
                            messages.remove(lastIdx);
                        }

                        messages.add(new AssistantMessage(finalText));
                        List<Message> saveNewMessages = messages.subList(historySizeBefore + 1, messages.size());
                        if (!saveNewMessages.isEmpty()) {
                            sessionStorageService.saveMessages(sessionId, new ArrayList<>(saveNewMessages));
                            log.info(">>> [STREAM] Saved streamed response, messages saved this turn: {}", saveNewMessages.size());
                        }
                        saveCompleted[0] = true;
                    } catch (Exception e) {
                        log.error(">>> [STREAM] Failed to save streamed response: {}", e.getMessage(), e);
                    }
                    log.info(">>> [STREAM] Complete, saved={}", saveCompleted[0]);
                    emitter.complete();
                }
            );
        } catch (Exception e) {
            log.error(">>> [STREAM] Failed to start stream: {}", e.getMessage(), e);
            try { emitter.send(SseEmitter.event().name("error").data("流式响应失败: " + e.getMessage())); }
            catch (IOException ignored) {}
            emitter.complete();
        }
    }

    /**
     * 清除指定会话的全部对话历史。
     *
     * @param sessionId 会话标识
     */
    public void clearHistory(String sessionId) {
        sessionStorageService.deleteSession(sessionId);
        contextCompressor.reset();
        log.info("Conversation history cleared for session: {}", sessionId);
    }

    /**
     * 获取指定会话的对话历史。
     */
    public List<Message> getConversationHistory(String sessionId) {
        return sessionStorageService.loadSession(sessionId);
    }

    /**
     * 手动触发指定会话的上下文压缩。
     *
     * @param sessionId     会话标识
     * @return 压缩后消息数量
     */
    public int compressContext(String sessionId) {
        List<Message> messages = new ArrayList<>(sessionStorageService.loadSession(sessionId));
        if (messages.isEmpty()) {
            return 0;
        }
        int before = messages.size();
        messages = contextCompressor.compress(messages, compressionProperties.getContextLength());
        int after = messages.size();
        log.info("Manual compression for session {}: {} -> {} messages", sessionId, before, after);
        return after;
    }

    /**
     * 获取所有可用工具名称列表。
     * 通过反射扫描 toolObjects 中带有 @Tool 注解的方法。
     * 对于 CGLIB 代理类，使用其超类（原始工具类）来查找注解。
     */
    public List<String> getAvailableTools() {
        List<String> names = new ArrayList<>();
        for (Object bean : toolObjects) {
            Class<?> cls = bean.getClass();
            // CGLIB 代理的超类是原始工具类
            if (cls.getName().contains("$$")) {
                cls = cls.getSuperclass();
            }
            for (Method method : cls.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool tool = method.getAnnotation(Tool.class);
                    String name = tool.name().isEmpty() ? method.getName() : tool.name();
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * 获取会话存储服务（供 Controller 调用）。
     */
    public SessionStorageService getSessionStorageService() {
        return sessionStorageService;
    }

    /**
     * 获取上下文压缩器（供 Controller 调用）。
     */
    public ContextCompressor getContextCompressor() {
        return contextCompressor;
    }

    /**
     * 获取工具调用追踪器（供 Controller 调用）。
     */
    public ToolCallTracker getToolCallTracker() {
        return toolCallTracker;
    }
}
