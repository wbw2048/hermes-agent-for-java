package com.hermes.agent.prompt;

import com.hermes.agent.config.ErrorPatternProperties;
import com.hermes.agent.error.ErrorPatternTracker;
import com.hermes.agent.memory.MemoryManager;
import com.hermes.agent.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 系统提示构建器。
 * 组装智能体身份、上下文文件发现、长期记忆、已激活技能和配置的默认提示词。
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private final String defaultSystemPrompt;
    private final MemoryManager memoryManager;
    private final SkillManager skillManager;
    private final ErrorPatternTracker errorPatternTracker;
    private final ErrorPatternProperties errorPatternProperties;

    public PromptBuilder(
            @Value("${hermes.agent.default-system-prompt:}")
            String defaultSystemPrompt,
            MemoryManager memoryManager,
            SkillManager skillManager,
            ErrorPatternTracker errorPatternTracker,
            ErrorPatternProperties errorPatternProperties
    ) {
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.memoryManager = memoryManager;
        this.skillManager = skillManager;
        this.errorPatternTracker = errorPatternTracker;
        this.errorPatternProperties = errorPatternProperties;
    }

    /**
     * 构建系统提示（无会话，向后兼容）。
     *
     * @return 完整的系统提示文本
     */
    public String buildSystemPrompt() {
        return buildSystemPrompt(null);
    }

    /**
     * 构建系统提示（基于会话）。
     * 组装顺序：智能体身份 → 上下文文件发现（会话维度）→ 长期记忆快照 → 配置的默认提示词（兜底）。
     *
     * @param sessionId 会话 ID，用于定位会话级上下文文件
     * @return 完整的系统提示文本
     */
    public String buildSystemPrompt(String sessionId) {
        StringBuilder prompt = new StringBuilder();

        // 1. 角色（SOUL.md 或配置的默认提示词）
        String soulMd = ContextFileDiscovery.loadSoulMd();
        if (!soulMd.isEmpty()) {
            prompt.append("=== 角色 (Role) ===\n").append(soulMd);
        } else if (!defaultSystemPrompt.isEmpty()) {
            prompt.append("=== 角色 (Role) ===\n").append(defaultSystemPrompt);
        }

        // 2. 项目上下文（会话感知）
        String contextPrompt = ContextFileDiscovery.buildContextFilesPrompt(sessionId);
        if (!contextPrompt.isEmpty()) {
            prompt.append("\n\n=== 项目上下文 (Project Context) ===\n").append(contextPrompt);
        }

        // 3. 记忆 + 用户画像
        String memoryBlock = memoryManager.buildSystemPrompt();
        if (!memoryBlock.isEmpty()) {
            prompt.append("\n\n").append(memoryBlock);
        }

        // 4. 已激活技能
        String skillBlock = skillManager.buildSkillPromptBlock(sessionId);
        if (!skillBlock.isEmpty()) {
            prompt.append(skillBlock);
        }

        // 5. 经验教训
        java.util.List<String> lessons = errorPatternTracker.getRecentLessons(
            errorPatternProperties.getMaxLessonsInPrompt());
        if (!lessons.isEmpty()) {
            prompt.append("\n\n=== 经验教训 (Lessons Learned) ===\n");
            prompt.append("以下是你从过去的工具失败中积累的经验，请牢记并避免重复犯错：\n");
            for (int i = 0; i < lessons.size(); i++) {
                prompt.append(i + 1).append(". ").append(lessons.get(i)).append("\n");
            }
        }

        String result = prompt.toString();
        log.debug("System prompt built (sessionId={}): {} chars", sessionId, result.length());
        return result;
    }
}
