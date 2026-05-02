package com.hermes.agent.error;

/**
 * 标准化错误响应，作为 @RestControllerAdvice 的统一返回格式。
 */
public record StandardErrorResponse(
        String errorCode,
        String message,
        String details,
        long timestamp,
        String sessionId
) {
    /**
     * 工厂方法：从错误信息和上下文中构建响应。
     */
    public static StandardErrorResponse of(String errorCode, String userMessage,
                                           String details, String sessionId) {
        return new StandardErrorResponse(errorCode, userMessage, details,
                System.currentTimeMillis(), sessionId);
    }
}
