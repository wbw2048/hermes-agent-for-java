package com.hermes.agent.websocket;

/**
 * WebSocket 消息协议 Record。
 */
public record WsMessage(
        String type,
        String sessionId,
        String message,
        String data,
        String toolName,
        String arguments,
        String result,
        Long elapsedMs
) {
    /** 客户端 -> 服务端：聊天消息 */
    public static WsMessage chat(String sessionId, String message) {
        return new WsMessage("chat", sessionId, message, null, null, null, null, null);
    }

    /** 服务端 -> 客户端：文本流式片段 */
    public static WsMessage text(String data) {
        return new WsMessage("text", null, null, data, null, null, null, null);
    }

    /** 服务端 -> 客户端：工具调用开始 */
    public static WsMessage toolCall(String toolName, String arguments) {
        return new WsMessage("tool_call", null, null, null, toolName, arguments, null, null);
    }

    /** 服务端 -> 客户端：工具调用结果 */
    public static WsMessage toolResult(String toolName, String result, long elapsedMs) {
        return new WsMessage("tool_result", null, null, null, toolName, null, result, elapsedMs);
    }

    /** 服务端 -> 客户端：完成 */
    public static WsMessage done() {
        return new WsMessage("done", null, null, null, null, null, null, null);
    }

    /** 服务端 -> 客户端：错误 */
    public static WsMessage error(String data) {
        return new WsMessage("error", null, null, data, null, null, null, null);
    }
}
