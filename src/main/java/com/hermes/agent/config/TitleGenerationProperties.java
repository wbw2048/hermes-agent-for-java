package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 标题自动生成配置属性。
 */
@Component
@ConfigurationProperties(prefix = "hermes.agent.title")
public class TitleGenerationProperties {

    /** 是否启用自动标题生成 */
    private boolean enabled = true;

    /** LLM 调用超时秒数 */
    private int timeoutSeconds = 30;

    /** 标题最大字符数 */
    private int maxLength = 80;

    /** 截断用户/助手消息的字符数（发送给 LLM 生成标题） */
    private int snippetLength = 500;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    public int getMaxLength() { return maxLength; }
    public void setMaxLength(int maxLength) { this.maxLength = maxLength; }

    public int getSnippetLength() { return snippetLength; }
    public void setSnippetLength(int snippetLength) { this.snippetLength = snippetLength; }
}
