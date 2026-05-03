package com.hermes.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 技能管理器。
 * 按会话激活/停用技能，管理技能生命周期和预处理。
 */
@Component
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    private final SkillRegistry registry;
    private final SkillPreprocessor preprocessor;

    /** sessionId -> 已激活的技能名称集合 */
    private final Map<String, Set<String>> activeSkillsBySession = new ConcurrentHashMap<>();

    /** 全局激活的技能（对所有会话生效） */
    private final Set<String> globalActiveSkills = ConcurrentHashMap.newKeySet();

    public SkillManager(SkillRegistry registry, SkillPreprocessor preprocessor) {
        this.registry = registry;
        this.preprocessor = preprocessor;
    }

    /**
     * 为指定会话激活技能。
     *
     * @param sessionId 会话 ID
     * @param skillName 技能名称
     * @return 成功返回 true，技能不存在返回 false
     */
    public boolean activateSkill(String sessionId, String skillName) {
        if (registry.getSkill(skillName).isEmpty()) {
            log.warn("Cannot activate skill '{}': not found", skillName);
            return false;
        }
        activeSkillsBySession.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(skillName);
        log.info("Activated skill '{}' for session {}", skillName, sessionId);
        return true;
    }

    /**
     * 为指定会话停用技能。
     */
    public boolean deactivateSkill(String sessionId, String skillName) {
        Set<String> active = activeSkillsBySession.get(sessionId);
        if (active == null || !active.contains(skillName)) {
            return false;
        }
        active.remove(skillName);
        log.info("Deactivated skill '{}' for session {}", skillName, sessionId);
        return true;
    }

    /**
     * 全局激活技能（对所有会话生效）。
     */
    public boolean activateGlobalSkill(String skillName) {
        if (registry.getSkill(skillName).isEmpty()) {
            log.warn("Cannot globally activate skill '{}': not found", false);
            return false;
        }
        globalActiveSkills.add(skillName);
        log.info("Globally activated skill '{}'", skillName);
        return true;
    }

    /**
     * 全局停用技能。
     */
    public boolean deactivateGlobalSkill(String skillName) {
        if (globalActiveSkills.remove(skillName)) {
            log.info("Globally deactivated skill '{}'", skillName);
            return true;
        }
        return false;
    }

    /**
     * 获取会话的已激活技能（含全局）。
     */
    public Set<String> getActiveSkills(String sessionId) {
        Set<String> result = new LinkedHashSet<>(globalActiveSkills);
        Set<String> sessionSkills = activeSkillsBySession.get(sessionId);
        if (sessionSkills != null) {
            result.addAll(sessionSkills);
        }
        return result;
    }

    /**
     * 获取全局激活的技能。
     */
    public Set<String> getGlobalActiveSkills() {
        return Set.copyOf(globalActiveSkills);
    }

    /**
     * 为会话构建技能注入块（用于系统提示）。
     * 将已激活技能的描述和内容注入到系统提示中。
     *
     * @param sessionId 会话 ID
     * @return 格式化的技能注入文本
     */
    public String buildSkillPromptBlock(String sessionId) {
        Set<String> active = getActiveSkills(sessionId);
        if (active.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== 已激活技能 (Active Skills) ===\n");

        for (String name : active) {
            registry.getSkill(name).ifPresent(skill -> {
                String processedContent = preprocessor.preprocess(
                        skill.content(), skill.skillDir(), sessionId);
                sb.append("\n--- 技能: ").append(name).append(" ---\n");
                sb.append(processedContent);
                sb.append("\n");
            });
        }

        return sb.toString();
    }

    /**
     * 清除会话的技能激活状态。
     */
    public void clearSessionSkills(String sessionId) {
        activeSkillsBySession.remove(sessionId);
    }
}
