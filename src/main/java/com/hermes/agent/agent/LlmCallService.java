package com.hermes.agent.agent;

import com.hermes.agent.config.ErrorHandlingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.List;

/**
 * LLM 调用服务，提供带重试机制的 ChatClient 调用封装。
 * 使用 @Retryable 实现自动重试，@Recover 实现重试失败后的恢复逻辑。
 */
@Service
public class LlmCallService {

    private static final Logger log = LoggerFactory.getLogger(LlmCallService.class);

    private final ChatClient chatClient;
    private final ErrorHandlingProperties errorHandlingProperties;

    public LlmCallService(ChatClient.Builder chatClientBuilder, ErrorHandlingProperties errorHandlingProperties) {
        this.chatClient = chatClientBuilder.build();
        this.errorHandlingProperties = errorHandlingProperties;
    }

    /**
     * 调用 LLM，支持自动重试。
     * 仅对 TransientAiException 和 ConnectException 进行重试。
     */
    @Retryable(
            retryFor = {TransientAiException.class, ConnectException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000)
    )
    public AssistantMessage callLlmWithRetry(String systemPrompt, List<Message> messages, Object[] tools) {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .tools(tools)
                .call()
                .chatResponse();
        return response.getResult().getOutput();
    }

    /**
     * 调用 LLM 工具循环（不返回文本，仅完成工具调用）。
     */
    @Retryable(
            retryFor = {TransientAiException.class, ConnectException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000)
    )
    public ChatResponse callToolLoopWithRetry(String systemPrompt, List<Message> messages, Object[] tools) {
        return chatClient.prompt()
                .system(systemPrompt)
                .messages(messages)
                .tools(tools)
                .call()
                .chatResponse();
    }

    /**
     * 重试全部失败后的恢复方法。
     */
    @Recover
    public AssistantMessage recoverFromLlmFailure(Throwable e, String systemPrompt, List<Message> messages, Object[] tools) {
        int maxRetries = errorHandlingProperties.getLlmMaxRetries();
        log.error(">>> [LLM-RECOVER] All {} retries exhausted: {}", maxRetries, e.getMessage());
        throw new RuntimeException("LLM 调用失败，已重试 " + maxRetries + " 次: " + e.getMessage(), e);
    }

    @Recover
    public ChatResponse recoverFromToolLoopFailure(Throwable e, String systemPrompt, List<Message> messages, Object[] tools) {
        int maxRetries = errorHandlingProperties.getLlmMaxRetries();
        log.error(">>> [LLM-RECOVER] Tool loop failed, all {} retries exhausted: {}", maxRetries, e.getMessage());
        throw new RuntimeException("工具循环失败，已重试 " + maxRetries + " 次: " + e.getMessage(), e);
    }
}
