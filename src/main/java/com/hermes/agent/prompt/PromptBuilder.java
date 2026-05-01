package com.hermes.agent.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 系统提示构建器。
 * 组装智能体身份、上下文文件发现和配置的默认提示词。
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    /** 默认智能体身份（翻译自 Python 参考实现的 DEFAULT_AGENT_IDENTITY）。 */
    private static final String DEFAULT_AGENT_IDENTITY =
        "你是 Hermes Agent，一个由 Nous Research 开发的智能 AI 助手。" +
        "你乐于助人、知识丰富且直接。你协助用户完成各种任务，包括回答问题、编写和编辑代码、" +
        "分析信息、创造性工作以及通过工具执行操作。" +
        "你沟通清晰，在适当时承认不确定性，并优先提供真正有用的帮助，而非冗长叙述。" +
        "在探索和研究中要有针对性和高效。";

    private final String defaultSystemPrompt;

    public PromptBuilder(
            @Value("${hermes.agent.default-system-prompt:}")
            String defaultSystemPrompt
    ) {
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

    /**
     * 构建系统提示。
     * 组装顺序：智能体身份 → 上下文文件发现 → 配置的默认提示词（兜底）。
     *
     * @return 完整的系统提示文本
     */
    public String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();

        // 1. 智能体身份（SOUL.md 或默认）
        String soulMd = ContextFileDiscovery.loadSoulMd();
        prompt.append(!soulMd.isEmpty() ? soulMd : DEFAULT_AGENT_IDENTITY);

        // 2. 上下文文件发现
        String contextPrompt = ContextFileDiscovery.buildContextFilesPrompt(Path.of(System.getProperty("user.dir")));
        if (!contextPrompt.isEmpty()) {
            prompt.append("\n\n").append(contextPrompt);
        }

        // 3. 配置的默认提示词兜底（当无 SOUL.md 且无上下文文件时使用）
        if (prompt.isEmpty() && !defaultSystemPrompt.isEmpty()) {
            prompt.append(defaultSystemPrompt);
        }

        String result = prompt.toString();
        log.debug("System prompt built: {} chars", result.length());
        return result;
    }
}
