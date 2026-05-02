package com.hermes.agent.tool.builtin;

import com.hermes.agent.config.MemoryProperties;
import com.hermes.agent.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 记忆管理工具。
 * <p>
 * 允许智能体在对话中主动保存和修改长期记忆。
 * 记忆持久化到文件系统，跨会话保留。
 */
@Component
public class MemoryTools {

    private static final Logger log = LoggerFactory.getLogger(MemoryTools.class);

    private final MemoryStore memoryStore;
    private final MemoryProperties properties;

    public MemoryTools(MemoryStore memoryStore, MemoryProperties properties) {
        this.memoryStore = memoryStore;
        this.properties = properties;
    }

    /**
     * 向长期记忆添加条目。
     * <p>
     * 当用户纠正你、分享偏好、或你发现环境事实时主动调用。
     * 不要在记忆中保存任务进度或临时状态。
     *
     * @param action  操作类型：add
     * @param target  目标存储："memory"（智能体笔记）或 "user"（用户画像）
     * @param content 条目内容
     * @param oldText 不适用（仅 add 操作不需要）
     * @return 操作结果 JSON
     */
    @Tool(description = """
        Save durable information to persistent memory that survives across sessions.
        Memory is injected into future turns, so keep it compact and focused on facts
        that will still matter later.

        WHEN TO SAVE (do this proactively, don't wait to be asked):
        - User corrects you or says 'remember this' / 'don't do that again'
        - User shares a preference, habit, or personal detail (name, role, timezone, coding style)
        - You discover something about the environment (OS, installed tools, project structure)
        - You learn a convention, API quirk, or workflow specific to this user's setup
        - You identify a stable fact that will be useful again in future sessions

        PRIORITY: User preferences and corrections > environment facts > procedural knowledge.
        The most valuable memory prevents the user from having to repeat themselves.

        Do NOT save task progress, session outcomes, completed-work logs, or temporary TODO
        state to memory.

        TWO TARGETS:
        - 'user': who the user is -- name, role, preferences, communication style, pet peeves
        - 'memory': your notes -- environment facts, project conventions, tool quirks, lessons learned

        IMPORTANT: You CANNOT write to 'soul' (agent role/personality). Only the user can modify
        the agent's role through the UI. Attempting to write to 'soul' will be rejected.

        SKIP: trivial/obvious info, things easily re-discovered, raw data dumps, and temporary task state.
        """)
    public String memory(
            @ToolParam(description = "The action to perform: add, replace, remove") String action,
            @ToolParam(description = "Which memory store: 'memory' for personal notes, 'user' for user profile") String target,
            @ToolParam(description = "The entry content. Required for 'add' and 'replace'.") String content,
            @ToolParam(description = "Short unique substring identifying the entry to replace or remove.",
                    required = false) String oldText) {

        if (!properties.isEnabled()) {
            return "{\"success\": false, \"error\": \"Memory is disabled in configuration.\"}";
        }

        // 角色设定（SOUL.md）仅用户可通过 UI 修改，智能体不可写入
        if ("soul".equals(target)) {
            return "{\"success\": false, \"error\": \"Cannot write to 'soul' target. Agent role (SOUL.md) can only be modified by the user through the UI.\"}";
        }

        return switch (action) {
            case "add" -> {
                if (content == null || content.isBlank()) {
                    yield "{\"success\": false, \"error\": \"Content is required for 'add' action.\"}";
                }
                yield toJson(memoryStore.add(target, content));
            }
            case "replace" -> {
                if (oldText == null || oldText.isBlank()) {
                    yield "{\"success\": false, \"error\": \"old_text is required for 'replace' action.\"}";
                }
                if (content == null || content.isBlank()) {
                    yield "{\"success\": false, \"error\": \"content is required for 'replace' action.\"}";
                }
                yield toJson(memoryStore.replace(target, oldText, content));
            }
            case "remove" -> {
                if (oldText == null || oldText.isBlank()) {
                    yield "{\"success\": false, \"error\": \"old_text is required for 'remove' action.\"}";
                }
                yield toJson(memoryStore.remove(target, oldText));
            }
            default -> String.format(
                "{\"success\": false, \"error\": \"Unknown action '%s'. Use: add, replace, remove\"}", action);
        };
    }

    private static String toJson(Map<String, Object> result) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof String s) {
                sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
            } else if (value instanceof Boolean b) {
                sb.append(b);
            } else if (value instanceof Number n) {
                sb.append(n);
            } else if (value instanceof java.util.List<?> list) {
                sb.append("[");
                boolean firstItem = true;
                for (Object item : list) {
                    if (!firstItem) sb.append(", ");
                    firstItem = false;
                    if (item instanceof String s) {
                        sb.append("\"").append(s.replace("\"", "\\\"")).append("\"");
                    } else {
                        sb.append(item);
                    }
                }
                sb.append("]");
            } else {
                sb.append(value != null ? "\"" + value.toString().replace("\"", "\\\"") + "\"" : "null");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
