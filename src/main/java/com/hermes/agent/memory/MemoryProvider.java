package com.hermes.agent.memory;

import java.util.List;
import java.util.Map;

/**
 * 可插拔的记忆提供者抽象接口。
 * <p>
 * 记忆提供者赋予智能体跨会话的记忆能力。内置 MemoryStore 始终作为第一个 provider 存在，
 * 外部 provider（如向量数据库、第三方记忆服务）作为可选的附加提供者。
 */
public interface MemoryProvider {

    /**
     * 提供者短名称（如 "builtin"、"mem0"）。
     */
    String name();

    /**
     * 检查 provider 是否已配置并就绪。不应发起网络调用。
     */
    boolean isAvailable();

    /**
     * 为指定会话初始化 provider。
     *
     * @param sessionId 会话 ID
     * @param kwargs    额外参数（hermesHome, platform 等）
     */
    void initialize(String sessionId, Map<String, Object> kwargs);

    /**
     * 返回要注入系统提示的静态文本。返回空字符串则跳过。
     */
    default String systemPromptBlock() {
        return "";
    }

    /**
     * 为即将到来的对话轮次预取相关记忆上下文。
     *
     * @param query 当前用户消息
     * @return 格式化的记忆上下文文本，空字符串表示无相关内容
     */
    default String prefetch(String query) {
        return "";
    }

    /**
     * 后台预取队列，为下一轮对话做准备。默认无操作。
     */
    default void queuePrefetch(String query) {
    }

    /**
     * 同步已完成的一轮对话到后端。应非阻塞。
     *
     * @param userContent     用户消息内容
     * @param assistantContent 助手响应内容
     */
    default void syncTurn(String userContent, String assistantContent) {
    }

    /**
     * 返回此提供者暴露的工具 schema 列表。
     * 每个 schema 格式：{name, description, parameters}
     */
    List<Map<String, Object>> getToolSchemas();

    /**
     * 处理此提供者的工具调用。
     *
     * @param toolName 工具名称
     * @param args     工具参数
     * @return JSON 字符串结果
     */
    String handleToolCall(String toolName, Map<String, Object> args);

    /**
     * 清理关闭。
     */
    default void shutdown() {
    }

    // --- 可选钩子 ---

    default void onTurnStart(int turnNumber, String message, Map<String, Object> kwargs) {
    }

    default void onSessionEnd(List<Map<String, String>> messages) {
    }

    default void onSessionSwitch(String newSessionId, String parentSessionId, boolean reset) {
    }

    default String onPreCompress(List<Map<String, String>> messages) {
        return "";
    }
}
