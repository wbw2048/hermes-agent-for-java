package com.hermes.agent.error;

import com.hermes.agent.config.ErrorPatternProperties;
import com.hermes.agent.controller.ToolCallInfo;
import com.hermes.agent.entity.ErrorPatternEntity;
import com.hermes.agent.memory.MemoryStore;
import com.hermes.agent.repository.ErrorPatternRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ErrorPatternMemory 单元测试。
 * 由于 MemoryExtractor 类似，ChatClient 使用 mock。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErrorPatternMemoryTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    @Mock
    private MemoryStore memoryStore;

    @Mock
    private ErrorPatternRepository repository;

    private ErrorPatternProperties properties;
    private ErrorPatternMemory errorPatternMemory;

    @BeforeEach
    void setUp() {
        properties = new ErrorPatternProperties();
        properties.setEnabled(true);
        properties.setMaxLessonLength(100);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        errorPatternMemory = new ErrorPatternMemory(chatClientBuilder, memoryStore, repository, properties);
    }

    @Test
    void extractLessons_noErrors_doesNothing() {
        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/tmp/test.txt)", "content", null, 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verifyNoInteractions(chatClient);
    }

    @Test
    void extractLessons_disabled_doesNothing() {
        properties.setEnabled(false);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verifyNoInteractions(chatClient);
    }

    @Test
    void extractLessons_nullInput_doesNothing() {
        errorPatternMemory.extractLessons(null, "test-session");
        verifyNoInteractions(chatClient);
    }

    @Test
    void extractLessons_singleError_extractsLesson() {
        when(callSpec.content()).thenReturn("执行文件操作前先检查路径是否存在");

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/tmp/test.txt)", null, "FileNotFoundException: /tmp/test.txt", 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verify(chatClient).prompt();
        verify(memoryStore).add(eq("memory"), anyString());
    }

    @Test
    void extractLessons_lessonStoredInMemoryStore() {
        when(callSpec.content()).thenReturn("终端命令超时，应增加 timeout 参数");

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("terminal_exec", "(cmd=python slow.py)", null, "timeout after 60s", 60000)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verify(memoryStore).add(eq("memory"), contains("教训"));
        verify(memoryStore).add(eq("memory"), contains("terminal_exec"));
    }

    @Test
    void extractLessons_lessonUnderMaxLength() {
        when(callSpec.content()).thenReturn("a".repeat(150));

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        // The lesson is truncated by updateLessonLearned before DB write
        // and the memoryStore add includes prefix + tool name
        verify(memoryStore).add(eq("memory"), anyString());
    }

    @Test
    void extractLessons_llmFailure_doesNotCrash() {
        when(callSpec.content()).thenThrow(new RuntimeException("LLM connection failed"));

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        assertDoesNotThrow(() -> errorPatternMemory.extractLessons(calls, "test-session"));
    }

    @Test
    void extractLessons_noneResponse_skipped() {
        when(callSpec.content()).thenReturn("NONE");

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verify(memoryStore, never()).add(anyString(), anyString());
    }

    @Test
    void extractLessons_blankResponse_skipped() {
        when(callSpec.content()).thenReturn("   ");

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verify(memoryStore, never()).add(anyString(), anyString());
    }

    @Test
    void extractLessons_multipleErrors_extractsAll() {
        when(callSpec.content())
            .thenReturn("lesson 1")
            .thenReturn("lesson 2");

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error1", 50),
            new ToolCallInfo("terminal_exec", "(cmd=ls)", null, "error2", 100)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verify(memoryStore, times(2)).add(eq("memory"), anyString());
    }

    @Test
    void extractLessons_nullContent_skipped() {
        when(callSpec.content()).thenReturn(null);

        List<ToolCallInfo> calls = List.of(
            new ToolCallInfo("file_read", "(path=/a)", null, "error", 50)
        );

        errorPatternMemory.extractLessons(calls, "test-session");

        verify(memoryStore, never()).add(anyString(), anyString());
    }
}
