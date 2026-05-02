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
        sb.append("\n\n## Memory (Persistent Notes)\n");
        sb.append("The following is your persistent memory, loaded at session start.\n");
        sb.append("These notes are stable across turns — do not re-save them unless they have changed.\n");

        if (memoryBlock != null) {
            sb.append("\n").append(memoryBlock);
        } else {
            sb.append("\n(No memory notes yet)\n");
        }

        if (userBlock != null) {
            sb.append("\n\n").append(userBlock);
        } else {
            sb.append("\n(No user profile yet)\n");
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
