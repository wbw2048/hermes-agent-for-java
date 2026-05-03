package com.hermes.agent.error;

import com.hermes.agent.config.ErrorPatternProperties;
import com.hermes.agent.controller.ToolCallInfo;
import com.hermes.agent.entity.ErrorPatternEntity;
import com.hermes.agent.error.ErrorClassifier.ErrorType;
import com.hermes.agent.repository.ErrorPatternRepository;
import com.hermes.agent.workspace.SessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 错误模式追踪器。
 * <p>
 * 接收 ToolCallTracker 产出的工具调用列表，筛选有错误的调用，
 * 写入 SQLite error_patterns 表，并检测重复模式。
 */
@Component
public class ErrorPatternTracker {

    private static final Logger log = LoggerFactory.getLogger(ErrorPatternTracker.class);

    private final ErrorPatternRepository repository;
    private final ErrorClassifier errorClassifier;
    private final ErrorPatternProperties properties;

    public ErrorPatternTracker(
            ErrorPatternRepository repository,
            ErrorClassifier errorClassifier,
            ErrorPatternProperties properties
    ) {
        this.repository = repository;
        this.errorClassifier = errorClassifier;
        this.properties = properties;
    }

    /**
     * 记录本轮工具调用中的错误模式。
     *
     * @param sessionId 会话 ID（优先使用，若为空则回退到 SessionContext）
     * @param toolCalls ToolCallTracker 产出的本轮工具调用列表
     */
    public void recordErrors(String sessionId, List<ToolCallInfo> toolCalls) {
        if (!properties.isEnabled() || toolCalls == null) {
            return;
        }

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = SessionContext.get();
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = "unknown";
            }
        }

        for (ToolCallInfo call : toolCalls) {
            // 工具方法抛出的异常（error 字段非空）
            if (call.error() != null && !call.error().isBlank()) {
                recordSingleError(sessionId, call);
                continue;
            }

            // 工具方法返回的 JSON 错误（result 字段中包含 "error" key）
            if (call.result() != null && call.result().contains("\"error\"")) {
                recordSingleError(sessionId, call);
                continue;
            }
        }
    }

    private void recordSingleError(String sessionId, ToolCallInfo call) {
        String errorText = (call.error() != null && !call.error().isBlank()) ? call.error() : call.result();
        ErrorType errorType = errorClassifier.classify(new RuntimeException(errorText));

        boolean isRepeat = detectRepeat(call.toolName(), errorType);

        ErrorPatternEntity entity = new ErrorPatternEntity();
        entity.setSessionId(sessionId);
        entity.setToolName(call.toolName());
        entity.setArgumentSummary(truncate(call.arguments(), 200));
        entity.setErrorType(errorType.name());
        entity.setErrorSnippet(truncate(errorText, 500));
        entity.setOccurredAt(Instant.now());
        entity.setRepeat(isRepeat);

        repository.save(entity);
        log.info("[ERROR-PATTERN] Recorded: tool={} type={} repeat={}",
            call.toolName(), errorType, isRepeat);
    }

    /**
     * 获取最近 N 条已提取的教训，供 PromptBuilder 注入。
     *
     * @param limit 最大返回条数
     * @return 教训文本列表
     */
    public List<String> getRecentLessons(int limit) {
        return repository.findRecentLessons(limit).stream()
            .map(ErrorPatternEntity::getLessonLearned)
            .toList();
    }

    // --- 内部方法 ---

    private boolean detectRepeat(String toolName, ErrorType errorType) {
        Instant window = Instant.now().minus(
            Duration.ofHours(properties.getRepeatDetectionWindowHours()));
        return repository.countRecentByToolAndType(toolName, errorType.name(), window) > 0;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
