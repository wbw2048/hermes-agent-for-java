package com.hermes.agent.prompt;

import com.hermes.agent.config.MemoryProperties;
import com.hermes.agent.memory.MemoryManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PromptBuilder} 的测试。
 */
class PromptBuilderTest {

    private MemoryManager createMemoryManager() {
        MemoryProperties props = new MemoryProperties();
        props.setEnabled(false);
        return new MemoryManager(props);
    }

    @Test
    void buildsPromptWithDefaultIdentity() {
        PromptBuilder builder = new PromptBuilder("你是 Hermes Agent，一个智能助手。", createMemoryManager());
        String prompt = builder.buildSystemPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        assertTrue(prompt.contains("Hermes Agent"));
    }

    @Test
    void buildsPromptWithSessionId() {
        PromptBuilder builder = new PromptBuilder("你是 Hermes Agent，一个智能助手。", createMemoryManager());
        String prompt = builder.buildSystemPrompt("test-session-id");
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        assertTrue(prompt.contains("Hermes Agent"));
    }

    @Test
    void buildsPromptWithNullSessionIdSameAsNoArg() {
        PromptBuilder builder = new PromptBuilder("你是默认助手。", createMemoryManager());
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
}
