package com.hermes.agent.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 错误模式实体，映射 error_patterns 表。
 * 记录工具调用失败的模式，用于重复检测和教训提取。
 */
@Entity
@Table(name = "error_patterns", indexes = {
    @Index(name = "idx_error_patterns_session", columnList = "session_id"),
    @Index(name = "idx_error_patterns_tool_type", columnList = "tool_name, error_type")
})
public class ErrorPatternEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "argument_summary", length = 1000)
    private String argumentSummary;

    @Column(name = "error_type", nullable = false, length = 50)
    private String errorType;

    @Column(name = "error_snippet", columnDefinition = "TEXT")
    private String errorSnippet;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "lesson_learned", length = 500)
    private String lessonLearned;

    @Column(name = "is_repeat", nullable = false)
    private boolean repeat = false;

    public ErrorPatternEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getArgumentSummary() { return argumentSummary; }
    public void setArgumentSummary(String argumentSummary) { this.argumentSummary = argumentSummary; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getErrorSnippet() { return errorSnippet; }
    public void setErrorSnippet(String errorSnippet) { this.errorSnippet = errorSnippet; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public String getLessonLearned() { return lessonLearned; }
    public void setLessonLearned(String lessonLearned) { this.lessonLearned = lessonLearned; }

    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }
}
