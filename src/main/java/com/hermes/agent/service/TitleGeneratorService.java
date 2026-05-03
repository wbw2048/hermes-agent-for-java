package com.hermes.agent.service;

import com.hermes.agent.config.TitleGenerationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * 会话标题自动生成服务。
 * <p>
 * 在首次对话完成后，后台异步调用 LLM 生成简短描述性标题。
 * 失败时静默跳过，不影响正常对话流程。
 */
@Service
public class TitleGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(TitleGeneratorService.class);

    private static final String TITLE_PROMPT = """
        Generate a short, descriptive title (3-7 words) for a conversation that starts with the \
        following exchange. The title should capture the main topic or intent. \
        Return ONLY the title text, nothing else. No quotes, no punctuation at the end, no prefixes.
        """;

    private final ChatClient chatClient;
    private final TitleGenerationProperties properties;
    private final SessionStorageService sessionStorageService;

    public TitleGeneratorService(
            ChatClient.Builder chatClientBuilder,
            TitleGenerationProperties properties,
            SessionStorageService sessionStorageService
    ) {
        this.chatClient = chatClientBuilder.build();
        this.properties = properties;
        this.sessionStorageService = sessionStorageService;
    }

    /**
     * 异步生成会话标题。
     * <p>
     * 仅在会话尚无标题时执行，不阻塞调用方。
     *
     * @param sessionId          会话 ID
     * @param userMessage        用户第一条消息
     * @param assistantResponse  助手第一条回复
     */
    public void generateTitleAsync(String sessionId, String userMessage, String assistantResponse) {
        if (!properties.isEnabled()) {
            log.debug("Title generation disabled, skipping for session {}", sessionId);
            return;
        }

        // 检查是否已有标题
        var session = sessionStorageService.getSession(sessionId);
        if (session != null && session.getTitle() != null && !session.getTitle().isBlank()) {
            log.debug("Session {} already has title '{}', skipping auto-title", sessionId, session.getTitle());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String title = callLlmForTitle(userMessage, assistantResponse);
                if (title != null && !title.isBlank()) {
                    sessionStorageService.updateSessionTitle(sessionId, title);
                    log.info("Auto-generated title for session {}: '{}'", sessionId, title);
                }
            } catch (Exception e) {
                log.warn("Title generation failed for session {}: {}", sessionId, e.getMessage());
            }
        });
    }

    private String callLlmForTitle(String userMessage, String assistantResponse) {
        String userSnippet = truncate(userMessage, properties.getSnippetLength());
        String assistantSnippet = truncate(assistantResponse, properties.getSnippetLength());

        String content = TITLE_PROMPT + "\n\nUser: " + userSnippet + "\n\nAssistant: " + assistantSnippet;

        String response = chatClient.prompt()
                .messages(org.springframework.ai.chat.messages.UserMessage.builder().text(content).build())
                .call()
                .content();

        if (response == null) return null;

        String title = response.trim();
        // 清理：去除引号、前缀
        title = title.replaceAll("^\"|\"$", "");
        title = title.replaceAll("^'|'$", "");
        if (title.toLowerCase().startsWith("title:")) {
            title = title.substring(6).trim();
        }
        if (title.toLowerCase().startsWith("标题:")) {
            title = title.substring(3).trim();
        }

        // 长度限制
        int maxLen = properties.getMaxLength();
        if (title.length() > maxLen) {
            title = title.substring(0, maxLen - 3) + "...";
        }

        return title.isBlank() ? null : title;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
