package com.hermes.agent.workspace;

/**
 * 线程级别的当前会话上下文。
 * <p>
 * 供工具类在执行时获取当前 sessionId，实现 workspace 沙箱边界检查。
 */
public final class SessionContext {

    private static final ThreadLocal<String> CURRENT_SESSION = new ThreadLocal<>();

    private SessionContext() {}

    public static void set(String sessionId) {
        CURRENT_SESSION.set(sessionId);
    }

    public static String get() {
        return CURRENT_SESSION.get();
    }

    public static void clear() {
        CURRENT_SESSION.remove();
    }
}
