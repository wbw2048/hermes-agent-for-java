package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 上下文压缩配置属性。
 */
@Configuration
@ConfigurationProperties(prefix = "hermes.agent.context-compression")
public class ContextCompressionProperties {

    /** 是否启用上下文压缩 */
    private boolean enabled = true;

    /** 触发压缩的阈值百分比（0.75 = 75%） */
    private double thresholdPercent = 0.75;

    /** 保护头部消息数量 */
    private int protectFirstN = 3;

    /** 保护尾部消息数量 */
    private int protectLastN = 6;

    /** 尾部令牌预算 */
    private int tailTokenBudget = 20000;

    /** 模型上下文窗口大小（令牌数），用于计算实际阈值 */
    private int contextLength = 128000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getThresholdPercent() {
        return thresholdPercent;
    }

    public void setThresholdPercent(double thresholdPercent) {
        this.thresholdPercent = thresholdPercent;
    }

    public int getProtectFirstN() {
        return protectFirstN;
    }

    public void setProtectFirstN(int protectFirstN) {
        this.protectFirstN = protectFirstN;
    }

    public int getProtectLastN() {
        return protectLastN;
    }

    public void setProtectLastN(int protectLastN) {
        this.protectLastN = protectLastN;
    }

    public int getTailTokenBudget() {
        return tailTokenBudget;
    }

    public void setTailTokenBudget(int tailTokenBudget) {
        this.tailTokenBudget = tailTokenBudget;
    }

    public int getContextLength() {
        return contextLength;
    }

    public void setContextLength(int contextLength) {
        this.contextLength = contextLength;
    }

    /**
     * 计算触发压缩的实际令牌阈值。
     */
    public int getThresholdTokens() {
        return (int) (contextLength * thresholdPercent);
    }
}
