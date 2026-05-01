package com.hermes.agent.controller;

/**
 * 单次工具调用的详细信息。
 * <p>
 * 由 ToolCallTracker 通过动态代理拦截 @Tool 方法调用时生成。
 */
public record ToolCallInfo(
        String toolName,
        String arguments,
        String result,
        String error,
        long elapsedMs
) {}
