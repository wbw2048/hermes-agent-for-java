package com.hermes.agent.agent;

import com.hermes.agent.service.SessionStorageService;
import com.hermes.agent.tool.ToolSetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
    private final String defaultSystemPrompt;
    private final SessionStorageService sessionStorageService;

    /**
     * 创建智能体实例。
     *
     * @param chatClientBuilder      Spring AI ChatClient 构建器
     * @param toolSetManager         工具集管理器，负责按配置过滤工具 Bean
     * @param allToolBeans           所有工具 Bean
     * @param sessionStorageService  会话存储服务
     * @param defaultSystemPrompt    系统提示词，每次请求都会注入
     */
    public SimpleAgent(
            ChatClient.Builder chatClientBuilder,
            ToolSetManager toolSetManager,
            List<Object> allToolBeans,
            SessionStorageService sessionStorageService,
            @Value("${hermes.agent.default-system-prompt:你是一个有帮助的AI助手。请用中文回答问题。}")
            String defaultSystemPrompt
    ) {
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.chatClient = chatClientBuilder.build();
        this.sessionStorageService = sessionStorageService;
        List<Object> activeBeans = toolSetManager.getActiveToolBeans(allToolBeans);
        this.toolObjects = filterToolBeans(activeBeans);
        log.info("SimpleAgent initialized with {} tool beans (toolsets={}): {}",
                this.toolObjects.length, toolSetManager.getActiveToolSetNames(), getAvailableTools());
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
        log.info(">>> [LLM-REQUEST] sessionId={}", sessionId);

        // 确保会话存在
        sessionStorageService.createSession(sessionId, null);

        // 从数据库加载历史
        List<Message> messages = sessionStorageService.loadSession(sessionId);

        // 追加用户消息
        messages = new ArrayList<>(messages);
        messages.add(new UserMessage(userMessage));

        log.info(">>> [LLM-MESSAGES] sessionId={}, total messages in history={}", sessionId, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            Message m = messages.get(i);
            log.info("  [{}] type={} text={}", i, m.getMessageType(), m.getText());
        }
        log.info(">>> [LLM-SYSTEM] {}", defaultSystemPrompt.replaceAll("\\n", "\\\\n"));

        try {
            var chatResponse = chatClient.prompt()
                    .system(defaultSystemPrompt)
                    .messages(messages)
                    .tools(toolObjects)
                    .call()
                    .chatResponse();

            var assistantMsg = chatResponse.getResult().getOutput();
            String content = assistantMsg.getText();

            log.info(">>> [LLM-RESPONSE] sessionId={}, responseText={}", sessionId, content != null ? content.replaceAll("\\n", "\\\\n") : "(null)");
            log.info(">>> [LLM-RESPONSE-DETAILS] messageType={}, toolCalls={}",
                    assistantMsg.getMessageType(), assistantMsg.getToolCalls());

            // 仅保存本轮新增的消息（用户消息 + 助手消息），不重复保存历史
            messages.add(assistantMsg);
            List<Message> newMessages = messages.subList(Math.max(0, messages.size() - 2), messages.size());
            sessionStorageService.saveMessages(sessionId, newMessages);

            return content != null ? content : "";
        } catch (Exception e) {
            log.error(">>> [LLM-ERROR] sessionId={}: {}", sessionId, e.getMessage(), e);
            return "抱歉，处理您的请求时出现了错误: " + e.getMessage();
        }
    }

    /**
     * 清除指定会话的全部对话历史。
     *
     * @param sessionId 会话标识
     */
    public void clearHistory(String sessionId) {
        sessionStorageService.deleteSession(sessionId);
        log.info("Conversation history cleared for session: {}", sessionId);
    }

    /**
     * 获取指定会话的对话历史。
     */
    public List<Message> getConversationHistory(String sessionId) {
        return sessionStorageService.loadSession(sessionId);
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

    /**
     * 获取会话存储服务（供 Controller 调用）。
     */
    public SessionStorageService getSessionStorageService() {
        return sessionStorageService;
    }
}
