package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 长期记忆系统配置属性。
 */
@Configuration
@ConfigurationProperties(prefix = "hermes.agent.memory")
public class MemoryProperties {

    /** 是否启用长期记忆 */
    private boolean enabled = true;

    /** MEMORY.md 字符限制 */
    private int memoryCharLimit = 2200;

    /** USER.md 字符限制 */
    private int userCharLimit = 1375;

    /** 记忆文件存储目录（默认 ~/.hermes/memories） */
    private String homeDir = System.getProperty("user.home") + "/.hermes/memories";

    /** 是否启用自动记忆提取（对话后自动提取关键信息） */
    private boolean autoExtract = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMemoryCharLimit() {
        return memoryCharLimit;
    }

    public void setMemoryCharLimit(int memoryCharLimit) {
        this.memoryCharLimit = memoryCharLimit;
    }

    public int getUserCharLimit() {
        return userCharLimit;
    }

    public void setUserCharLimit(int userCharLimit) {
        this.userCharLimit = userCharLimit;
    }

    public String getHomeDir() {
        return homeDir;
    }

    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }

    public boolean isAutoExtract() {
        return autoExtract;
    }

    public void setAutoExtract(boolean autoExtract) {
        this.autoExtract = autoExtract;
    }
}
