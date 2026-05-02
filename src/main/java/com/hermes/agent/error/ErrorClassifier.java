package com.hermes.agent.error;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.stereotype.Component;

/**
 * 错误分类器，根据异常类型和消息内容将错误归类。
 * 每种类型对应不同的重试策略和用户提示。
 */
@Component
public class ErrorClassifier {

    public enum ErrorType {
        TIMEOUT,       // 超时 - 可重试，较长 backoff
        RATE_LIMIT,    // API 限流 - 可重试，指数 backoff
        AUTH_FAILURE,  // 认证失败 - 不可重试
        TOOL_ERROR,    // 工具异常 - 可隔离，对话继续
        NETWORK_ERROR, // 网络异常 - 可重试，短 backoff
        UNKNOWN        // 未知错误 - 有限重试
    }

    /**
     * 根据异常类型和消息内容将错误归类。
     */
    public ErrorType classify(Throwable e) {
        if (e == null) return ErrorType.UNKNOWN;

        String msg = (e.getMessage() != null ? e.getMessage() : "").toLowerCase();

        if (e instanceof TransientAiException) {
            if (msg.contains("timeout") || msg.contains("timed out")) {
                return ErrorType.TIMEOUT;
            }
            if (msg.contains("429") || msg.contains("rate limit") || msg.contains("throttl")) {
                return ErrorType.RATE_LIMIT;
            }
            return ErrorType.NETWORK_ERROR;
        }

        if (e instanceof NonTransientAiException) {
            if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("auth")) {
                return ErrorType.AUTH_FAILURE;
            }
            if (msg.contains("429") || msg.contains("rate limit") || msg.contains("throttl")) {
                return ErrorType.RATE_LIMIT;
            }
            return ErrorType.UNKNOWN;
        }

        if (msg.contains("[tracker]")) {
            return ErrorType.TOOL_ERROR;
        }

        if (msg.contains("timeout") || msg.contains("timed out")
                || msg.contains("connect timed out") || msg.contains("read timed out")) {
            return ErrorType.TIMEOUT;
        }
        if (msg.contains("429") || msg.contains("rate limit") || msg.contains("throttl")) {
            return ErrorType.RATE_LIMIT;
        }
        if (msg.contains("401") || msg.contains("unauthorized") || msg.contains("forbidden") || msg.contains("403")) {
            return ErrorType.AUTH_FAILURE;
        }
        if (msg.contains("connection refused") || msg.contains("unknown host")
                || msg.contains("socket") || msg.contains("io exception")) {
            return ErrorType.NETWORK_ERROR;
        }

        return ErrorType.UNKNOWN;
    }

    /**
     * 返回用户友好的中文错误提示。
     */
    public String getUserMessage(ErrorType type) {
        return switch (type) {
            case TIMEOUT -> "请求超时，正在重试...";
            case RATE_LIMIT -> "API 调用过于频繁，请稍后再试";
            case AUTH_FAILURE -> "API 认证失败，请检查配置";
            case TOOL_ERROR -> "工具执行出错，已跳过";
            case NETWORK_ERROR -> "网络连接异常，正在重试...";
            case UNKNOWN -> "处理请求时出现未知错误";
        };
    }
}
