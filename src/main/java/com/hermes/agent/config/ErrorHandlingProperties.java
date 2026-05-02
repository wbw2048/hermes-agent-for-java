package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 hermes.agent.error-handling.* 配置属性。
 */
@ConfigurationProperties(prefix = "hermes.agent.error-handling")
public class ErrorHandlingProperties {

    /** LLM 调用最大重试次数 */
    private int llmMaxRetries = 3;
    /** 初始退避时间（毫秒） */
    private long backoffInitialMs = 1000;
    /** 退避乘数 */
    private double backoffMultiplier = 2.0;
    /** 最大退避时间（毫秒） */
    private long backoffMaxMs = 30000;
    /** 工具调用超时（毫秒） */
    private long toolTimeoutMs = 30000;
    /** 是否启用工具异常隔离 */
    private boolean toolErrorIsolationEnabled = true;
    /** 工具调用失败时返回给 LLM 的通用错误提示模板 */
    private String toolErrorMessageTemplate = "工具执行失败: {toolName}。错误: {error}";

    public int getLlmMaxRetries() { return llmMaxRetries; }
    public void setLlmMaxRetries(int llmMaxRetries) { this.llmMaxRetries = llmMaxRetries; }
    public long getBackoffInitialMs() { return backoffInitialMs; }
    public void setBackoffInitialMs(long backoffInitialMs) { this.backoffInitialMs = backoffInitialMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public void setBackoffMultiplier(double backoffMultiplier) { this.backoffMultiplier = backoffMultiplier; }
    public long getBackoffMaxMs() { return backoffMaxMs; }
    public void setBackoffMaxMs(long backoffMaxMs) { this.backoffMaxMs = backoffMaxMs; }
    public long getToolTimeoutMs() { return toolTimeoutMs; }
    public void setToolTimeoutMs(long toolTimeoutMs) { this.toolTimeoutMs = toolTimeoutMs; }
    public boolean isToolErrorIsolationEnabled() { return toolErrorIsolationEnabled; }
    public void setToolErrorIsolationEnabled(boolean toolErrorIsolationEnabled) { this.toolErrorIsolationEnabled = toolErrorIsolationEnabled; }
    public String getToolErrorMessageTemplate() { return toolErrorMessageTemplate; }
    public void setToolErrorMessageTemplate(String toolErrorMessageTemplate) { this.toolErrorMessageTemplate = toolErrorMessageTemplate; }
}
