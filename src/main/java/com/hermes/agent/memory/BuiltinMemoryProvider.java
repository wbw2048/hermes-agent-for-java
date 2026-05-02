package com.hermes.agent.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置记忆提供者，包装 MemoryStore 实现。
 * 负责文件存储的记忆管理和工具暴露。
 */
@Component
public class BuiltinMemoryProvider implements MemoryProvider {

    private static final Logger log = LoggerFactory.getLogger(BuiltinMemoryProvider.class);

    private final MemoryStore memoryStore;

    public BuiltinMemoryProvider(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 获取底层的 MemoryStore（用于测试）。
     */
    public MemoryStore getMemoryStore() {
        return memoryStore;
    }

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void initialize(String sessionId, Map<String, Object> kwargs) {
        memoryStore.loadFromDisk();
        log.info("BuiltinMemoryProvider initialized for session {}", sessionId);
    }

    @Override
    public String systemPromptBlock() {
        String memoryBlock = memoryStore.formatForSystemPrompt("memory");
        String userBlock = memoryStore.formatForSystemPrompt("user");

        if (memoryBlock == null && userBlock == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 记忆 (Memory) ===\n");
        sb.append("以下为你在跨会话中保存的事实笔记，内容在轮次间保持稳定，除非有变化否则不要重新保存。\n");

        if (memoryBlock != null) {
            sb.append("\n").append(memoryBlock);
        } else {
            sb.append("\n(暂无记忆笔记)\n");
        }

        sb.append("\n\n=== 用户画像 (User Profile) ===\n");
        sb.append("以下为关于用户的个人信息（偏好、习惯、角色等）。\n");

        if (userBlock != null) {
            sb.append("\n").append(userBlock);
        } else {
            sb.append("\n(暂无用户画像)\n");
        }

        return sb.toString();
    }

    @Override
    public List<Map<String, Object>> getToolSchemas() {
        // MemoryTools 通过 @Tool 注解由 Spring AI 自动注册，这里返回空列表
        // 工具 schema 由 Spring AI 从 @Tool 方法生成
        return List.of();
    }

    @Override
    public String handleToolCall(String toolName, Map<String, Object> args) {
        return null; // 工具由 Spring AI 通过 @Tool 注解自动处理
    }

    @Override
    public void syncTurn(String userContent, String assistantContent) {
        // 内置 provider 不自动同步——记忆提取由 MemoryExtractor 处理
    }
}
