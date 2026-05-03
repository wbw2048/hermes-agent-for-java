package com.hermes.agent.error;

import com.hermes.agent.config.ErrorPatternProperties;
import com.hermes.agent.controller.ToolCallInfo;
import com.hermes.agent.entity.ErrorPatternEntity;
import com.hermes.agent.repository.ErrorPatternRepository;
import com.hermes.agent.workspace.SessionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ErrorPatternTracker 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ErrorPatternTrackerTest {

    @Mock
    private ErrorPatternRepository repository;

    @Mock
    private ErrorClassifier errorClassifier;

    private ErrorPatternProperties properties;
    private ErrorPatternTracker tracker;

    @BeforeEach
    void setUp() {
        properties = new ErrorPatternProperties();
        properties.setEnabled(true);
        properties.setRepeatDetectionWindowHours(24);
        properties.setMaxLessonLength(100);
        tracker = new ErrorPatternTracker(repository, errorClassifier, properties);
        SessionContext.set("test-session");
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    @Test
    void recordErrors_noErrors_doesNothing() {
        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/tmp/test.txt)", "content here", null, 50)
        );

        tracker.recordErrors("test-session", calls);

        verifyNoInteractions(repository);
    }

    @Test
    void recordErrors_singleError_recorded() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/tmp/test.txt)", null, "FileNotFoundException: /tmp/test.txt", 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());

        ErrorPatternEntity entity = captor.getValue();
        assertEquals("test-session", entity.getSessionId());
        assertEquals("file_read", entity.getToolName());
        assertEquals("TOOL_ERROR", entity.getErrorType());
        assertTrue(entity.getErrorSnippet().contains("FileNotFoundException"));
        assertFalse(entity.isRepeat());
    }

    @Test
    void recordErrors_multipleErrors_allRecorded() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error1", 50),
            new ToolCallInfo("terminal_exec", "(cmd=ls)", null, "error2", 100),
            new ToolCallInfo("file_write", "(path=/b)", "ok", "error3", 30)
        );

        tracker.recordErrors("test-session", calls);

        verify(repository, times(3)).save(any());
    }

    @Test
    void recordErrors_nullError_skipped() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", "ok", null, 50),
            new ToolCallInfo("file_read", "(path=/b)", null, "real error", 50)
        );

        tracker.recordErrors("test-session", calls);

        verify(repository, times(1)).save(any());
    }

    @Test
    void recordErrors_emptyError_skipped() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", "ok", "", 50),
            new ToolCallInfo("file_read", "(path=/b)", null, "   ", 50),
            new ToolCallInfo("file_read", "(path=/c)", null, "real error", 50)
        );

        tracker.recordErrors("test-session", calls);

        verify(repository, times(1)).save(any());
    }

    @Test
    void recordErrors_detectsRepeat() {
        when(repository.countRecentByToolAndType(eq("file_read"), eq("TOOL_ERROR"), any())).thenReturn(1L);
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().isRepeat());
    }

    @Test
    void recordErrors_notRepeat_differentTool() {
        // Use lenient stubbing since we're checking for different tool name
        lenient().when(repository.countRecentByToolAndType(eq("file_read"), eq("TOOL_ERROR"), any())).thenReturn(1L);
        lenient().when(repository.countRecentByToolAndType(eq("terminal_exec"), eq("TOOL_ERROR"), any())).thenReturn(0L);
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("terminal_exec", "(cmd=ls)", null, "error", 50)
        );

        tracker.recordErrors("test-session", calls);

        verify(repository).countRecentByToolAndType(eq("terminal_exec"), eq("TOOL_ERROR"), any(Instant.class));
    }

    @Test
    void recordErrors_notRepeat_outsideWindow() {
        when(repository.countRecentByToolAndType(any(), any(), any())).thenReturn(0L);
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());
        assertFalse(captor.getValue().isRepeat());
    }

    @Test
    void recordErrors_disabled_doesNothing() {
        properties.setEnabled(false);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        tracker.recordErrors("test-session", calls);

        verifyNoInteractions(repository);
    }

    @Test
    void recordErrors_truncatesLongArguments() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);
        String longArgs = "(".repeat(300) + "data)";

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", longArgs, null, "error", 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getArgumentSummary().length() <= 203);
    }

    @Test
    void recordErrors_truncatesLongErrorSnippet() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);
        String longError = "e".repeat(600) + " end";

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, longError, 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getErrorSnippet().length() <= 503);
    }

    @Test
    void recordErrors_usesSessionContext() {
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("test-session", captor.getValue().getSessionId());
    }

    @Test
    void recordErrors_detectsErrorInResult() {
        // 工具方法返回 JSON 错误（不抛异常）
        when(errorClassifier.classify(any(RuntimeException.class))).thenReturn(ErrorClassifier.ErrorType.TOOL_ERROR);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("readFile", "(path=/nonexistent)", "{\"error\": \"File not found\"}", null, 50)
        );

        tracker.recordErrors("test-session", calls);

        ArgumentCaptor<ErrorPatternEntity> captor = ArgumentCaptor.forClass(ErrorPatternEntity.class);
        verify(repository).save(captor.capture());
        ErrorPatternEntity saved = captor.getValue();
        assertEquals("readFile", saved.getToolName());
        assertEquals("{\"error\": \"File not found\"}", saved.getErrorSnippet());
    }

    @Test
    void recordErrors_ignoresNonErrorResult() {
        // 工具返回正常结果（不含 "error" key），不应被记录
        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("readFile", "(path=/existing)", "{\"content\": \"hello\"}", null, 50)
        );

        tracker.recordErrors("test-session", calls);

        verifyNoInteractions(repository);
    }

    @Test
    void recordErrors_ignoresResultContainingNonJsonError() {
        // result 中包含 "error" 字符串但不是 JSON error key，不应被记录
        // 注意：当前实现检查的是 "\"error\"" 而非 "error"，所以这个不会触发
        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("search", "(pattern=error)", "Found error in log file", null, 50)
        );

        tracker.recordErrors("test-session", calls);

        verifyNoInteractions(repository);
    }

    @Test
    void getRecentLessons_returnsOnlyNonNull() {
        // Only entities with lessonLearned are returned by the repository query
        List<ErrorPatternEntity> entities = List.of(
            createEntityWithLesson("lesson 1"),
            createEntityWithLesson("lesson 2")
        );

        when(repository.findRecentLessons(5)).thenReturn(entities);

        List<String> result = tracker.getRecentLessons(5);

        assertEquals(2, result.size());
        assertEquals("lesson 1", result.get(0));
        assertEquals("lesson 2", result.get(1));
    }

    @Test
    void recordErrors_nullInput_doesNothing() {
        tracker.recordErrors(null, null);
        verifyNoInteractions(repository);
    }

    private ErrorPatternEntity createEntityWithLesson(String lesson) {
        ErrorPatternEntity e = new ErrorPatternEntity();
        e.setLessonLearned(lesson);
        return e;
    }

    private ErrorPatternEntity createEntityWithoutLesson() {
        ErrorPatternEntity e = new ErrorPatternEntity();
        e.setLessonLearned(null);
        return e;
    }
}
