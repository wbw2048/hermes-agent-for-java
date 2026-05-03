package com.hermes.agent.mcp;

/**
 * MCP 服务器连接状态枚举。
 */
public enum McpConnectionState {
    /** 未连接 */
    DISCONNECTED,
    /** 正在连接 */
    CONNECTING,
    /** 已连接且可用 */
    CONNECTED,
    /** 连接失败或异常 */
    ERROR,
    /** 已关闭（不再尝试重连） */
    SHUTDOWN
}
