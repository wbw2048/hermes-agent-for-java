package com.hermes.agent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带工具调用功能的 AI 智能体。
 * <p>
 * 将用户消息追加到会话历史后，调用 LLM 并让 Spring AI 自动处理工具调用循环，
 * 最终返回助手响应。工具调用由 Spring AI 的 ToolCallingManager 自动管理。
 * <p>
 * 在内存中管理每个会话的对话历史。
 */
@Component
public class SimpleAgent {

    private static final Logger log = LoggerFactory.getLogger(SimpleAgent.class);

    private final ChatClient chatClient;
    private final Object[] toolObjects;
    private final String defaultSystemPrompt;

    // Per-session conversation history using Spring AI message types
    private final ConcurrentHashMap<String, List<Message>> sessionMessages = new ConcurrentHashMap<>();

    /**
     * 创建智能体实例。
     *
     * @param chatClientBuilder   Spring AI ChatClient 构建器
     * @param toolObjects         所有带有 @Tool 注解方法的 Spring Bean（由 Spring 自动注入）
     * @param defaultSystemPrompt 系统提示词，每次请求都会注入
     */
    public SimpleAgent(
            ChatClient.Builder chatClientBuilder,
            List<Object> toolObjects,
            @Value("${hermes.agent.default-system-prompt:你是一个有帮助的AI助手。请用中文回答问题。}")
            String defaultSystemPrompt
    ) {
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.chatClient = chatClientBuilder.build();
        this.toolObjects = filterToolBeans(toolObjects);
        log.info("SimpleAgent initialized with {} tool beans: {}",
                this.toolObjects.length, getAvailableTools());
    }

    /**
     * 过滤出仅包含 @Tool 方法的 Bean，避免向 Spring AI 注册无工具方法的 Bean 导致错误。
     */
    private static Object[] filterToolBeans(List<Object> beans) {
        return beans.stream()
                .filter(bean -> {
                    for (Method m : bean.getClass().getDeclaredMethods()) {
                        if (m.isAnnotationPresent(Tool.class)) return true;
                    }
                    return false;
                })
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
        log.debug("Running conversation: sessionId={}, message={}", sessionId, userMessage);

        List<Message> messages = sessionMessages.computeIfAbsent(sessionId, k -> new ArrayList<>());
        messages.add(new UserMessage(userMessage));

        try {
            var chatResponse = chatClient.prompt()
                    .system(defaultSystemPrompt)
                    .messages(messages)
                    .tools(toolObjects)
                    .call()
                    .chatResponse();

            var assistantMsg = chatResponse.getResult().getOutput();
            String content = assistantMsg.getText();
            messages.add(assistantMsg);
            return content != null ? content : "";
        } catch (Exception e) {
            log.error("Error in conversation: {}", e.getMessage(), e);
            return "抱歉，处理您的请求时出现了错误: " + e.getMessage();
        }
    }

    /**
     * 清除指定会话的全部对话历史。
     *
     * @param sessionId 会话标识
     */
    public void clearHistory(String sessionId) {
        sessionMessages.remove(sessionId);
        log.info("Conversation history cleared for session: {}", sessionId);
    }

    /**
     * 获取指定会话的对话历史。
     */
    public List<Message> getConversationHistory(String sessionId) {
        List<Message> history = sessionMessages.get(sessionId);
        return history != null ? List.copyOf(history) : List.of();
    }

    /**
     * 获取所有可用工具名称列表。
     * 通过反射扫描 toolObjects 中带有 @Tool 注解的方法。
     */
    public List<String> getAvailableTools() {
        List<String> names = new ArrayList<>();
        for (Object bean : toolObjects) {
            for (Method method : bean.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class)) {
                    Tool tool = method.getAnnotation(Tool.class);
                    String name = tool.name().isEmpty() ? method.getName() : tool.name();
                    names.add(name);
                }
            }
        }
        return names;
    }
}
