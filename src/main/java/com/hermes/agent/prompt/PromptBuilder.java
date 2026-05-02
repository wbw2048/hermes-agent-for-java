package com.hermes.agent.prompt;

import com.hermes.agent.memory.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 系统提示构建器。
 * 组装智能体身份、上下文文件发现、长期记忆和配置的默认提示词。
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private final String defaultSystemPrompt;
    private final MemoryManager memoryManager;

    public PromptBuilder(
            @Value("${hermes.agent.default-system-prompt:}")
            String defaultSystemPrompt,
            MemoryManager memoryManager
    ) {
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.memoryManager = memoryManager;
    }

    /**
     * 构建系统提示。
     * 组装顺序：智能体身份 → 上下文文件发现 → 长期记忆快照 → 配置的默认提示词（兜底）。
     *
     * @return 完整的系统提示文本
     */
    public String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        // 1. 智能体身份（SOUL.md 或配置的默认提示词）
        String soulMd = ContextFileDiscovery.loadSoulMd();
        prompt.append(!soulMd.isEmpty() ? soulMd : defaultSystemPrompt);

        // 2. 上下文文件发现
        String contextPrompt = ContextFileDiscovery.buildContextFilesPrompt(Path.of(System.getProperty("user.dir")));
        if (!contextPrompt.isEmpty()) {
            prompt.append("\n\n").append(contextPrompt);
        }

        // 3. 长期记忆快照
        String memoryBlock = memoryManager.buildSystemPrompt();
        if (!memoryBlock.isEmpty()) {
            prompt.append("\n\n").append(memoryBlock);
        }

        String result = prompt.toString();
        log.debug("System prompt built: {} chars", result.length());
        return result;
    }
}
