package com.hermes.agent.error;

import com.hermes.agent.config.ErrorPatternProperties;
import com.hermes.agent.controller.ToolCallInfo;
import com.hermes.agent.entity.ErrorPatternEntity;
import com.hermes.agent.memory.MemoryStore;
import com.hermes.agent.repository.ErrorPatternRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 错误模式记忆提取器。
 * <p>
 * 对话结束后异步检查工具错误，用 LLM 提取可执行的教训，
 * 通过 MemoryStore 写入 MEMORY.md，并回填到 error_patterns 表。
 */
@Component
public class ErrorPatternMemory {

    private static final Logger log = LoggerFactory.getLogger(ErrorPatternMemory.class);

    private static final String LESSON_EXTRACTION_PROMPT = """
        分析以下工具调用失败的情况，提取一条简短的、可执行的经验教训。
        教训应该告诉智能体"下次遇到类似情况应该怎么做"。

        工具: %s
        参数: %s
        错误: %s

        要求：
        1. 只输出一条教训，不超过%d个字符
        2. 用第二人称（"你应该..."或直接指令）
        3. 聚焦于行动，不要描述错误本身
        4. 如果错误是临时性的（如超时），输出通用的重试策略
        5. 如果无法提取有用教训，输出 NONE

        示例：
        - 执行文件操作前先检查路径是否存在
        - 终端命令超时，应增加 timeout 参数或使用异步执行
        - NONE
        """.stripIndent();

    private final ChatClient chatClient;
    private final MemoryStore memoryStore;
    private final ErrorPatternRepository repository;
    private final ErrorPatternProperties properties;

    public ErrorPatternMemory(
            ChatClient.Builder chatClientBuilder,
            MemoryStore memoryStore,
            ErrorPatternRepository repository,
            ErrorPatternProperties properties
    ) {
        this.chatClient = chatClientBuilder.build();
        this.memoryStore = memoryStore;
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * 异步提取教训。
     * 由 SimpleAgent 在 finally 块中通过 CompletableFuture.runAsync 调用。
     *
     * @param toolCalls 本轮工具调用列表
     * @param sessionId 会话ID
     */
    public void extractLessons(List<ToolCallInfo> toolCalls, String sessionId) {
        if (!properties.isEnabled() || toolCalls == null) {
            return;
        }

        List<ToolCallInfo> failedCalls = toolCalls.stream()
            .filter(c -> c.error() != null && !c.error().isBlank())
            .toList();

        if (failedCalls.isEmpty()) {
            return;
        }

        for (ToolCallInfo call : failedCalls) {
            try {
                String lesson = extractLesson(call);
                if (lesson == null || lesson.equals("NONE") || lesson.isBlank()) {
                    continue;
                }

                // 通过 MemoryStore 写入 "memory" 目标
                memoryStore.add("memory",
                    "[教训] " + lesson + " （来自 " + call.toolName() + " 失败）");

                // 回填到 error_patterns 表
                updateLessonLearned(sessionId, call.toolName(), lesson);

                log.info("[ERROR-LESSON] Extracted: '{}' for tool={}", lesson, call.toolName());
            } catch (Exception e) {
                log.warn("[ERROR-LESSON] Failed to extract lesson for tool={}: {}",
                    call.toolName(), e.getMessage());
            }
        }
    }

    // --- 内部方法 ---

    private String extractLesson(ToolCallInfo call) {
        String prompt = LESSON_EXTRACTION_PROMPT.formatted(
            call.toolName(),
            truncate(call.arguments(), 200),
            truncate(call.error(), 300),
            properties.getMaxLessonLength()
        );

        String result = chatClient.prompt()
            .user(prompt)
            .call()
            .content();

        return result != null ? result.strip() : null;
    }

    private void updateLessonLearned(String sessionId, String toolName, String lesson) {
        var recent = repository.findBySessionIdOrderByOccurredAtDesc(sessionId).stream()
            .filter(e -> e.getToolName().equals(toolName) && e.getLessonLearned() == null)
            .findFirst();

        recent.ifPresent(e -> {
            e.setLessonLearned(truncate(lesson, properties.getMaxLessonLength()));
            repository.save(e);
        });
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
