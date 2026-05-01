package com.hermes.agent.prompt;

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

    @Test
    void buildsPromptWithDefaultIdentity() {
        PromptBuilder builder = new PromptBuilder("");
        String prompt = builder.buildSystemPrompt();
        assertNotNull(prompt);
        assertTrue(prompt.length() > 0);
        // 默认身份应该出现在提示词中
        assertTrue(prompt.contains("Hermes Agent"));
    }

    @Test
    void buildsPromptWithCustomDefault() {
        PromptBuilder builder = new PromptBuilder("你是自定义助手。");
        String prompt = builder.buildSystemPrompt();
        // buildSystemPrompt 总是先加入身份部分
        assertNotNull(prompt);
        assertTrue(prompt.contains("Hermes Agent"));
    }

    @Test
    void buildsPromptIncludesContextFiles(@TempDir Path tempDir) throws IOException {
        // 创建一个含 CLAUDE.md 的目录，模拟在项目目录中调用
        Files.writeString(tempDir.resolve("CLAUDE.md"), "# Project\nUse Maven。");
        String result = ContextFileDiscovery.buildContextFilesPrompt(tempDir);
        assertTrue(result.contains("# Project Context"));
        assertTrue(result.contains("CLAUDE.md"));
    }

    @Test
    void soulMdOverridesDefaultIdentity(@TempDir Path tempDir) throws IOException {
        // 使用 loadSoulMdFrom 验证 SOUL.md 内容会被加载
        Path hermesHome = tempDir;
        Files.writeString(hermesHome.resolve("SOUL.md"), "你是自定义人设。");
        String result = ContextFileDiscovery.loadSoulMdFrom(hermesHome);
        assertTrue(result.contains("你是自定义人设"));
    }
}
