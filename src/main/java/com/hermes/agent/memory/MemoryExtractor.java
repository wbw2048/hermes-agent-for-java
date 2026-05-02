package com.hermes.agent.memory;

import com.hermes.agent.config.MemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自动记忆提取器。
 * <p>
 * 在每轮对话后使用 LLM 提取关键信息（用户偏好、环境事实），自动存入长期记忆。
 * 由 MemoryProperties.autoExtract 控制是否启用。
 */
@Component
public class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    private static final String EXTRACTION_PROMPT = """
        Analyze this conversation turn and extract key information worth saving to long-term memory.

        Conversation:
        User: %s
        Assistant: %s

        Extract facts that would be useful in FUTURE conversations. Focus on:
        1. User preferences, corrections, or personal details (name, role, language, timezone, coding style)
        2. Environment facts (OS, installed tools, project structure, API quirks)
        3. Workflow habits or conventions specific to this user

        DO NOT extract: task progress, session outcomes, completed-work logs, temporary state, or obvious facts.

        Return your answer in this exact format (one entry per line, or "NONE" if nothing worth saving):
        USER: <fact about the user>
        MEMORY: <fact about environment/conventions>

        Keep each entry concise (under 100 characters). Only extract truly memorable facts.
        """.stripIndent();

    private final ChatClient chatClient;
    private final MemoryStore memoryStore;
    private final MemoryProperties properties;

    public MemoryExtractor(ChatClient.Builder chatClientBuilder, MemoryStore memoryStore, MemoryProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.memoryStore = memoryStore;
        this.properties = properties;
    }

    /**
     * 从一轮对话中提取记忆并自动保存。
     *
     * @param userContent     用户消息
     * @param assistantContent 助手响应
     */
    public void extract(String userContent, String assistantContent) {
        if (!properties.isAutoExtract() || !properties.isEnabled()) {
            return;
        }

        try {
            String prompt = EXTRACTION_PROMPT.formatted(
                truncate(userContent, 500),
                truncate(assistantContent, 500)
            );

            String result = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            if (result == null || result.isBlank() || result.trim().equals("NONE")) {
                return;
            }

            int saved = 0;
            for (String line : result.split("\n")) {
                line = line.strip();
                if (line.isEmpty() || line.equals("NONE")) continue;

                String target = null;
                String fact = null;

                if (line.startsWith("USER:")) {
                    target = "user";
                    fact = line.substring(5).strip();
                } else if (line.startsWith("MEMORY:")) {
                    target = "memory";
                    fact = line.substring(7).strip();
                }

                if (target != null && !fact.isEmpty()) {
                    Map<String, Object> addResult = memoryStore.add(target, fact);
                    if (Boolean.TRUE.equals(addResult.get("success"))) {
                        saved++;
                        log.info(">>> [MEMORY-EXTRACT] Auto-saved to {}: '{}'", target, fact);
                    }
                }
            }

            if (saved > 0) {
                log.info(">>> [MEMORY-EXTRACT] Extracted {} facts from conversation turn", saved);
            }
        } catch (Exception e) {
            // 提取失败不影响主对话流程
            log.warn(">>> [MEMORY-EXTRACT] Failed to extract memory: {}", e.getMessage());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
