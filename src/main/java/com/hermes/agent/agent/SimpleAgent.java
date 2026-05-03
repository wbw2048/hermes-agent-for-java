package com.hermes.agent.agent;

import com.hermes.agent.compressor.ContextCompressor;
import com.hermes.agent.compressor.TokenEstimator;
import com.hermes.agent.config.ContextCompressionProperties;
import com.hermes.agent.config.MemoryProperties;
import com.hermes.agent.config.TitleGenerationProperties;
import com.hermes.agent.controller.ToolCallInfo;
import com.hermes.agent.controller.ToolCallTracker;
import com.hermes.agent.error.ErrorClassifier;
import com.hermes.agent.error.ErrorClassifier.ErrorType;
import com.hermes.agent.memory.MemoryExtractor;
import com.hermes.agent.memory.MemoryManager;
import com.hermes.agent.prompt.PromptBuilder;
import com.hermes.agent.service.SessionStorageService;
import com.hermes.agent.service.TitleGeneratorService;
import com.hermes.agent.mcp.McpToolProvider;
import com.hermes.agent.tool.ToolSetManager;
import com.hermes.agent.websocket.WsMessage;
import com.hermes.agent.workspace.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
    private final LlmCallService llmCallService;
    private final ErrorClassifier errorClassifier;
    private final MemoryManager memoryManager;
    private final MemoryExtractor memoryExtractor;
    private final MemoryProperties memoryProperties;
    private final TitleGeneratorService titleGeneratorService;
    private final TitleGenerationProperties titleProperties;
    private final McpToolProvider mcpToolProvider;

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
     * @param llmCallService         LLM 调用服务（含重试机制）
     * @param errorClassifier        错误分类器
     * @param defaultSystemPrompt    系统提示词（向后兼容，若 PromptBuilder 未启用则使用）
     * @param mcpToolProvider        MCP 工具提供者，用于发现外部 MCP 服务器工具
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
            LlmCallService llmCallService,
            ErrorClassifier errorClassifier,
            MemoryManager memoryManager,
            MemoryExtractor memoryExtractor,
            MemoryProperties memoryProperties,
            TitleGeneratorService titleGeneratorService,
            TitleGenerationProperties titleProperties,
            McpToolProvider mcpToolProvider,
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
        this.llmCallService = llmCallService;
        this.errorClassifier = errorClassifier;
        this.memoryManager = memoryManager;
        this.memoryExtractor = memoryExtractor;
        this.memoryProperties = memoryProperties;
        this.titleGeneratorService = titleGeneratorService;
        this.titleProperties = titleProperties;
        this.mcpToolProvider = mcpToolProvider;
        List<Object> activeBeans = toolSetManager.getActiveToolBeans(allToolBeans);
        this.toolObjects = buildAllTools(activeBeans, mcpToolProvider);
        log.info("SimpleAgent initialized with {} tool beans (toolsets={}): {}",
            this.toolObjects.length, toolSetManager.getActiveToolSetNames(), getAvailableTools());
    }

    /**
     * 检查 Bean 是否包含 @Tool 方法。
     */
    private boolean hasToolMethods(Object bean) {
        for (Method m : bean.getClass().getDeclaredMethods()) {
            if (m.isAnnotationPresent(Tool.class)) return true;
        }
        return false;
    }

    /**
     * 构建全部工具数组：原生 @Tool Bean + MCP FunctionCallback。
     */
    private Object[] buildAllTools(List<Object> beans, McpToolProvider mcpToolProvider) {
        List<Object> allTools = new ArrayList<>();

        // 原生 @Tool Bean（包装追踪代理）
        List<Object> nativeTools = beans.stream()
            .filter(this::hasToolMethods)
            .map(toolCallTracker::wrap)
            .toList();
        allTools.addAll(nativeTools);

        // MCP 工具（来自外部 MCP 服务器）
        List<ToolCallback> mcpCallbacks = mcpToolProvider.discoverAllTools();
        allTools.addAll(mcpCallbacks);

        log.info("SimpleAgent: {} native tool(s) + {} MCP tool(s) = {} total",
            nativeTools.size(), mcpCallbacks.size(), allTools.size());

        return allTools.toArray();
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

        // 首次请求时初始化记忆系统
        if (memoryProperties.isEnabled()) {
            memoryManager.initializeAll(sessionId);
        }

        // 从数据库加载历史
        List<Message> messages = new ArrayList<>(sessionStorageService.loadSession(sessionId));
        boolean isNewSession = messages.isEmpty();

        // 追加用户消息
        messages.add(new UserMessage(userMessage));

        // 构建系统提示（包含记忆快照）
        String systemPrompt = promptBuilder.buildSystemPrompt(sessionId);
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
            SessionContext.set(sessionId);

            AssistantMessage assistantMsg = llmCallService.callLlmWithRetry(systemPrompt, messages, toolObjects);
            String content = assistantMsg.getText();

            log.info(">>> [LLM-RESPONSE] sessionId={}, responseText={}", sessionId, content != null ? content.replaceAll("\\n", "\\\\n") : "(null)");
            log.info(">>> [LLM-RESPONSE-DETAILS] messageType={}, toolCalls={}",
                assistantMsg.getMessageType(), assistantMsg.getToolCalls());

            // 保存本轮新增的所有消息（用户消息 + 中间 ToolResponseMessage + 最终助手消息）
            messages.add(assistantMsg);
            List<Message> newMessages = messages.subList(historySizeBefore, messages.size());
            sessionStorageService.saveMessages(sessionId, new ArrayList<>(newMessages));

            // 首次对话自动生成会话标题（异步，不阻塞响应）
            if (isNewSession) {
                triggerTitleGeneration(sessionId, userMessage, content);
            }

            return content != null ? content : "";
        } catch (Exception e) {
            ErrorType errorType = errorClassifier.classify(e);
            log.error(">>> [LLM-ERROR] sessionId={}, errorType={}: {}", sessionId, errorType, e.getMessage(), e);
            return "抱歉，处理您的请求时出现错误 (" + errorType.name().toLowerCase()
                + "): " + errorClassifier.getUserMessage(errorType);
        } finally {
            toolCallTracker.stopTracking();
            SessionContext.clear();
            // 记忆同步和自动提取（异步执行，不阻塞响应）
            if (memoryProperties.isEnabled()) {
                final String userMsg = userMessage;
                final String assistantText = messages.stream()
                    .filter(m -> m.getMessageType() == MessageType.ASSISTANT)
                    .map(Message::getText)
                    .reduce((a, b) -> b)
                    .orElse("");
                CompletableFuture.runAsync(() -> {
                    memoryManager.syncAll(userMsg, assistantText);
                    memoryManager.queuePrefetchAll(userMsg);
                    memoryExtractor.extract(userMsg, assistantText);
                });
            }
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

        // 首次请求时初始化记忆系统
        if (memoryProperties.isEnabled()) {
            memoryManager.initializeAll(sessionId);
        }

        List<Message> messages = new ArrayList<>(sessionStorageService.loadSession(sessionId));
        boolean isNewSession = messages.isEmpty();

        String systemPrompt = promptBuilder.buildSystemPrompt(sessionId);

        // 先添加用户消息到内存（不立即保存，等流式完成后统一保存）
        UserMessage userMsg = new UserMessage(message);
        int historySizeBefore = messages.size();
        messages.add(userMsg);

        // 第一阶段：完成工具调用循环（Spring AI 内部处理，中间消息不可见）
        toolCallTracker.startTracking();
        SessionContext.set(sessionId);
        try {
            ChatResponse toolResponse = llmCallService.callToolLoopWithRetry(systemPrompt, messages, toolObjects);

            // .call() 产生的最终助手消息（可能包含 toolCalls 或最终文本）
            AssistantMessage callAssistantMsg = toolResponse.getResult().getOutput();
            messages.add(callAssistantMsg);
        } catch (Exception e) {
            ErrorType errorType = errorClassifier.classify(e);
            log.error(">>> [STREAM-ERROR] tool loop failed, errorType={}: {}", errorType, e.getMessage(), e);
            try { emitter.send(SseEmitter.event().name("error")
                    .data("工具调用失败 (" + errorType.name().toLowerCase() + "): " + errorClassifier.getUserMessage(errorType))); }
            catch (IOException ignored) {}
            emitter.complete();
            toolCallTracker.stopTracking();
            SessionContext.clear();
            return;
        } finally {
            toolCallTracker.stopTracking();
            SessionContext.clear();
        }

        // 检查是否有工具执行失败
        boolean toolFailed = false;
        String toolErrorResult = null;
        for (ToolCallInfo tc : toolCallTracker.getCalls()) {
            try {
                emitter.send(SseEmitter.event().name("tool").data(tc));
            } catch (IOException e) {
                log.warn(">>> [STREAM] Failed to send tool event: {}", e.getMessage());
                emitter.complete();
                return;
            }
            if (tc.result() != null && tc.result().contains("error")) {
                toolFailed = true;
                toolErrorResult = tc.result();
            }
        }

        // 工具执行失败：直接返回错误结果，跳过慢速 LLM 解释
        if (toolFailed) {
            String errorMsg = "工具执行失败: " + toolErrorResult;
            safeSend(emitter, SseEmitter.event().name("text").data(errorMsg));
            List<Message> saveNewMessages = messages.subList(historySizeBefore, messages.size());
            if (!saveNewMessages.isEmpty()) {
                try { sessionStorageService.saveMessages(sessionId, new ArrayList<>(saveNewMessages)); }
                catch (Exception e) { log.error(">>> [STREAM] Failed to save error response: {}", e.getMessage()); }
            }
            if (isNewSession) {
                triggerTitleGeneration(sessionId, message, errorMsg);
            }
            emitter.complete();
            return;
        }

        // 第二阶段：流式输出文本（仅工具全部成功时执行）
        try {
            final StringBuilder streamedText = new StringBuilder();
            final boolean[] saveCompleted = {false};
            final boolean[] completed = {false};

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
                        catch (IOException e) {
                            log.warn(">>> [STREAM] Client disconnected, aborting: {}", e.getMessage());
                            emitter.complete();
                        }
                    }
                },
                error -> {
                    synchronized (completed) {
                        if (completed[0]) return;
                        completed[0] = true;
                    }
                    ErrorType errorType = errorClassifier.classify(error);
                    log.error(">>> [STREAM] Error, errorType={}: {}", errorType, error.getMessage(), error);
                    safeSend(emitter, SseEmitter.event().name("error")
                            .data("流式响应失败 (" + errorType.name().toLowerCase() + "): " + errorClassifier.getUserMessage(errorType)));
                    emitter.complete();
                },
                () -> {
                    synchronized (completed) {
                        if (completed[0]) return;
                        completed[0] = true;
                    }
                    try {
                        String finalText = streamedText.toString();
                        int lastIdx = messages.size() - 1;
                        if (lastIdx >= 0 && messages.get(lastIdx).getMessageType() == MessageType.ASSISTANT) {
                            messages.remove(lastIdx);
                        }
                        messages.add(new AssistantMessage(finalText));
                        List<Message> saveNewMessages = messages.subList(historySizeBefore, messages.size());
                        if (!saveNewMessages.isEmpty()) {
                            sessionStorageService.saveMessages(sessionId, new ArrayList<>(saveNewMessages));
                            log.info(">>> [STREAM] Saved {} messages (user + final assistant response)", saveNewMessages.size());
                        }
                        if (isNewSession) {
                            triggerTitleGeneration(sessionId, message, finalText);
                        }
                        saveCompleted[0] = true;
                    } catch (Exception e) {
                        log.error(">>> [STREAM] Failed to save streamed response: {}", e.getMessage(), e);
                    }
                    if (memoryProperties.isEnabled()) {
                        final String usrMsg = message;
                        final String txt = streamedText.toString();
                        CompletableFuture.runAsync(() -> {
                            memoryManager.syncAll(usrMsg, txt);
                            memoryManager.queuePrefetchAll(usrMsg);
                            memoryExtractor.extract(usrMsg, txt);
                        });
                    }
                    log.info(">>> [STREAM] Complete, saved={}", saveCompleted[0]);
                    emitter.complete();
                }
            );
        } catch (Exception e) {
            log.error(">>> [STREAM] Failed to start stream: {}", e.getMessage(), e);
            safeSend(emitter, SseEmitter.event().name("error").data("流式响应失败: " + e.getMessage()));
            emitter.complete();
        }
    }

    /**
     * WebSocket 版本的流式对话。两阶段方案与 SSE 版本相同：
     * 1. {@code .call()} 执行完整工具调用循环，推送 tool_call/tool_result 事件
     * 2. {@code .stream()} 复用消息历史，流式输出文本并推送 text 事件
     *
     * @param sessionId   会话标识
     * @param message     用户消息
     * @param wsSender    向客户端推送消息的回调
     */
    public void streamConversationWs(String sessionId, String message, Consumer<WsMessage> wsSender) {
        log.info(">>> [WS-REQUEST] sessionId={}", sessionId);

        sessionStorageService.createSession(sessionId, null);

        // 首次请求时初始化记忆系统
        if (memoryProperties.isEnabled()) {
            memoryManager.initializeAll(sessionId);
        }

        List<Message> messages = new ArrayList<>(sessionStorageService.loadSession(sessionId));
        boolean isNewSession = messages.isEmpty();

        String systemPrompt = promptBuilder.buildSystemPrompt(sessionId);

        UserMessage userMsg = new UserMessage(message);
        int historySizeBefore = messages.size();
        messages.add(userMsg);

        // 第一阶段：工具调用循环
        toolCallTracker.startTracking();
        SessionContext.set(sessionId);
        try {
            ChatResponse toolResponse = llmCallService.callToolLoopWithRetry(systemPrompt, messages, toolObjects);

            AssistantMessage callAssistantMsg = toolResponse.getResult().getOutput();
            messages.add(callAssistantMsg);
        } catch (Exception e) {
            ErrorType errorType = errorClassifier.classify(e);
            log.error(">>> [WS-ERROR] tool loop failed, errorType={}: {}", errorType, e.getMessage(), e);
            wsSender.accept(WsMessage.error("工具调用失败 (" + errorType.name().toLowerCase() + "): " + errorClassifier.getUserMessage(errorType)));
            wsSender.accept(WsMessage.done());
            toolCallTracker.stopTracking();
            SessionContext.clear();
            return;
        } finally {
            toolCallTracker.stopTracking();
            SessionContext.clear();
        }

        // 检查是否有工具执行失败
        boolean toolFailed = false;
        String toolErrorResult = null;
        for (ToolCallInfo tc : toolCallTracker.getCalls()) {
            wsSender.accept(WsMessage.toolCall(tc.toolName(), tc.arguments()));
            if (tc.result() != null && !tc.result().isBlank()) {
                wsSender.accept(WsMessage.toolResult(tc.toolName(), tc.result(), tc.elapsedMs()));
                if (tc.result().contains("error")) {
                    toolFailed = true;
                    toolErrorResult = tc.result();
                }
            }
        }

        // 工具执行失败：直接返回错误结果，跳过慢速 LLM 解释
        if (toolFailed) {
            wsSender.accept(WsMessage.text("工具执行失败: " + toolErrorResult));
            List<Message> saveNewMessages = messages.subList(historySizeBefore, messages.size());
            if (!saveNewMessages.isEmpty()) {
                try { sessionStorageService.saveMessages(sessionId, new ArrayList<>(saveNewMessages)); }
                catch (Exception e) { log.error(">>> [WS] Failed to save error response: {}", e.getMessage()); }
            }
            if (isNewSession) {
                triggerTitleGeneration(sessionId, message, "工具执行失败: " + toolErrorResult);
            }
            log.info(">>> [WS] Complete (tool error, skipped LLM explanation)");
            wsSender.accept(WsMessage.done());
            return;
        }

        // 第二阶段：流式输出文本（仅工具全部成功时执行）
        try {
            final StringBuilder streamedText = new StringBuilder();

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
                        wsSender.accept(WsMessage.text(text));
                    }
                },
                error -> {
                    log.error(">>> [WS] Stream error: {}", error.getMessage(), error);
                    ErrorType errorType = errorClassifier.classify(error);
                    wsSender.accept(WsMessage.error("流式响应失败 (" + errorType.name().toLowerCase() + "): " + errorClassifier.getUserMessage(errorType)));
                    wsSender.accept(WsMessage.done());
                },
                () -> {
                    try {
                        String finalText = streamedText.toString();
                        int lastIdx = messages.size() - 1;
                        if (lastIdx >= 0 && messages.get(lastIdx).getMessageType() == MessageType.ASSISTANT) {
                            messages.remove(lastIdx);
                        }
                        messages.add(new AssistantMessage(finalText));
                        List<Message> saveNewMessages = messages.subList(historySizeBefore, messages.size());
                        if (!saveNewMessages.isEmpty()) {
                            sessionStorageService.saveMessages(sessionId, new ArrayList<>(saveNewMessages));
                            log.info(">>> [WS] Saved {} messages", saveNewMessages.size());
                        }
                        if (isNewSession) {
                            triggerTitleGeneration(sessionId, message, finalText);
                        }
                    } catch (Exception e) {
                        log.error(">>> [WS] Failed to save response: {}", e.getMessage(), e);
                    }
                    if (memoryProperties.isEnabled()) {
                        final String usrMsg = message;
                        final String txt = streamedText.toString();
                        CompletableFuture.runAsync(() -> {
                            memoryManager.syncAll(usrMsg, txt);
                            memoryManager.queuePrefetchAll(usrMsg);
                            memoryExtractor.extract(usrMsg, txt);
                        });
                    }
                    log.info(">>> [WS] Complete");
                    wsSender.accept(WsMessage.done());
                }
            );
        } catch (Exception e) {
            ErrorType errorType = errorClassifier.classify(e);
            log.error(">>> [WS] Failed to start stream, errorType={}: {}", errorType, e.getMessage(), e);
            wsSender.accept(WsMessage.error("流式响应失败 (" + errorType.name().toLowerCase() + "): " + errorClassifier.getUserMessage(errorType)));
            wsSender.accept(WsMessage.done());
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
     * MCP 工具（FunctionCallback）通过 getName() 提取。
     */
    public List<String> getAvailableTools() {
        List<String> names = new ArrayList<>();
        for (Object obj : toolObjects) {
            if (obj instanceof ToolCallback callback) {
                names.add(callback.getToolDefinition().name());
                continue;
            }
            Class<?> cls = obj.getClass();
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

    /**
     * 触发异步标题生成（LLM 驱动）。
     */
    private void triggerTitleGeneration(String sessionId, String userMessage, String assistantResponse) {
        titleGeneratorService.generateTitleAsync(sessionId, userMessage, assistantResponse);
    }

    /**
     * 安全发送 SSE 事件，忽略已关闭的 emitter。
     */
    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); }
        catch (IOException e) { log.warn(">>> [STREAM] Failed to send SSE event: {}", e.getMessage()); }
    }
}
