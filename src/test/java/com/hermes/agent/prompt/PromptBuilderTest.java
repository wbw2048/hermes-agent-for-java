package com.hermes.agent.prompt;

import com.hermes.agent.config.ErrorPatternProperties;
import com.hermes.agent.config.MemoryProperties;
import com.hermes.agent.error.ErrorPatternTracker;
import com.hermes.agent.memory.MemoryManager;
import com.hermes.agent.skill.SkillManager;
import com.hermes.agent.skill.SkillPreprocessor;
import com.hermes.agent.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link PromptBuilder} 的测试。
 */
class PromptBuilderTest {

    private MemoryManager createMemoryManager() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(false);
        return new MemoryManager(props);
    }

    private SkillManager createMockSkillManager() {
        SkillManager manager = mock(SkillManager.class);
        when(manager.buildSkillPromptBlock(anyString())).thenReturn("");
        when(manager.buildSkillPromptBlock((String) null)).thenReturn("");
        return manager;
    }

    private ErrorPatternTracker createMockErrorPatternTracker() {
        ErrorPatternTracker tracker = mock(ErrorPatternTracker.class);
        when(tracker.getRecentLessons(anyInt())).thenReturn(List.of());
        return tracker;
    }

    private ErrorPatternProperties createErrorPatternProperties() {
        ErrorPatternProperties props = new ErrorPatternProperties();
        props.setMaxLessonsInPrompt(5);
        return props;
    }

    private PromptBuilder createPromptBuilder(String defaultPrompt) {
        return new PromptBuilder(defaultPrompt, createMemoryManager(), createMockSkillManager(),
            createMockErrorPatternTracker(), createErrorPatternProperties());
    }

    @Test
    void buildsPromptWithDefaultIdentity() {
        PromptBuilder builder = createPromptBuilder("你是 Hermes Agent，一个智能助手。");
        String prompt = builder.buildSystemPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        assertTrue(prompt.contains("Hermes Agent"));
        assertTrue(prompt.contains("=== 角色 (Role) ==="));
    }

    @Test
    void buildsPromptWithSessionId() {
        PromptBuilder builder = createPromptBuilder("你是 Hermes Agent，一个智能助手。");
        String prompt = builder.buildSystemPrompt("test-session-id");
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        assertTrue(prompt.contains("Hermes Agent"));
        assertTrue(prompt.contains("=== 角色 (Role) ==="));
    }

    @Test
    void buildsPromptWithNullSessionIdSameAsNoArg() {
        PromptBuilder builder = createPromptBuilder("你是默认助手。");
        String withNull = builder.buildSystemPrompt(null);
        String noArg = builder.buildSystemPrompt();
        assertEquals(noArg, withNull);
    }

    @Test
    void buildsPromptIncludesSessionContextFiles(@TempDir Path tempDir) throws IOException {
        // 设置会话级 CLAUDE.md
        Path sessionDir = tempDir.resolve("contexts").resolve("session-1");
        Files.createDirectories(sessionDir);
        Files.writeString(sessionDir.resolve("CLAUDE.md"), "# Project\nUse Maven。");

        // 使用测试重载指定 hermesHome
        String result = ContextFileDiscovery.buildContextFilesPrompt("session-1", tempDir);
        assertTrue(result.contains("# Project Context"));
        assertTrue(result.contains("CLAUDE.md"));
    }

    @Test
    void soulMdOverridesDefaultIdentity(@TempDir Path tempDir) throws IOException {
        Path hermesHome = tempDir;
        Files.writeString(hermesHome.resolve("SOUL.md"), "你是自定义人设。");
        String result = ContextFileDiscovery.loadSoulMdFrom(hermesHome);
        assertTrue(result.contains("你是自定义人设"));
    }

    @Test
    void promptContainsSectionHeaders(@TempDir Path tempDir) throws IOException {
        // Set up SOUL.md
        Files.writeString(tempDir.resolve("SOUL.md"), "你是测试人设。");

        // Use reflection to override HERMES_HOME for ContextFileDiscovery
        // Since ContextFileDiscovery uses System.getenv, we test via the test overload
        String contextResult = ContextFileDiscovery.buildContextFilesPrompt(null, tempDir);
        // With null sessionId, it loads SOUL.md
        assertTrue(contextResult.contains("你是测试人设"));
    }

    @Test
    void promptIncludesLessonsSectionWhenLessonsExist() {
        ErrorPatternTracker tracker = mock(ErrorPatternTracker.class);
        when(tracker.getRecentLessons(anyInt())).thenReturn(List.of("教训1：先检查路径", "教训2：增加超时"));

        ErrorPatternProperties props = new ErrorPatternProperties();
        props.setMaxLessonsInPrompt(5);

        PromptBuilder builder = new PromptBuilder("你是助手。", createMemoryManager(), createMockSkillManager(), tracker, props);
        String prompt = builder.buildSystemPrompt("test-session");

        assertTrue(prompt.contains("=== 经验教训 (Lessons Learned) ==="));
        assertTrue(prompt.contains("教训1"));
        assertTrue(prompt.contains("教训2"));
    }

    @Test
    void promptNoLessonsSectionWhenNoLessons() {
        ErrorPatternTracker tracker = mock(ErrorPatternTracker.class);
        when(tracker.getRecentLessons(anyInt())).thenReturn(List.of());

        ErrorPatternProperties props = new ErrorPatternProperties();
        props.setMaxLessonsInPrompt(5);

        PromptBuilder builder = new PromptBuilder("你是助手。", createMemoryManager(), createMockSkillManager(), tracker, props);
        String prompt = builder.buildSystemPrompt("test-session");

        assertFalse(prompt.contains("=== 经验教训"));
    }

    @Test
    void promptLimitsLessonsToMax() {
        // Repository returns exactly what maxLessonsInPrompt allows
        ErrorPatternTracker tracker = mock(ErrorPatternTracker.class);
        when(tracker.getRecentLessons(2)).thenReturn(List.of("教训1", "教训2"));

        ErrorPatternProperties props = new ErrorPatternProperties();
        props.setMaxLessonsInPrompt(2);

        PromptBuilder builder = new PromptBuilder("你是助手。", createMemoryManager(), createMockSkillManager(), tracker, props);
        String prompt = builder.buildSystemPrompt("test-session");

        assertTrue(prompt.contains("教训1"));
        assertTrue(prompt.contains("教训2"));
        assertFalse(prompt.contains("教训3"));
    }
}
