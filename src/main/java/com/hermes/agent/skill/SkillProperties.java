package com.hermes.agent.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Skills 系统配置属性。
 * 绑定 hermes.skills.* 配置项。
 */
@ConfigurationProperties(prefix = "hermes.skills")
public class SkillProperties {

    /** 是否启用 Skills 系统 */
    private boolean enabled = true;

    /** 外部技能目录列表 */
    private java.util.List<String> externalDirs = java.util.List.of();

    /** 禁用的技能名称 */
    private java.util.List<String> disabled = java.util.List.of();

    /** 按平台禁用的技能: { "macos": ["skill-a"], "linux": ["skill-b"] } */
    private Map<String, java.util.List<String>> platformDisabled = new HashMap<>();

    /** 模板变量替换是否启用 */
    private boolean templateVars = true;

    /** inline shell 展开是否启用（默认关闭，安全性考虑） */
    private boolean inlineShell = false;

    /** inline shell 超时秒数 */
    private int inlineShellTimeout = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public java.util.List<String> getExternalDirs() { return externalDirs; }
    public void setExternalDirs(java.util.List<String> externalDirs) { this.externalDirs = externalDirs; }
    public java.util.List<String> getDisabled() { return disabled; }
    public void setDisabled(java.util.List<String> disabled) { this.disabled = disabled; }
    public Map<String, java.util.List<String>> getPlatformDisabled() { return platformDisabled; }
    public void setPlatformDisabled(Map<String, java.util.List<String>> platformDisabled) { this.platformDisabled = platformDisabled; }
    public boolean isTemplateVars() { return templateVars; }
    public void setTemplateVars(boolean templateVars) { this.templateVars = templateVars; }
    public boolean isInlineShell() { return inlineShell; }
    public void setInlineShell(boolean inlineShell) { this.inlineShell = inlineShell; }
    public int getInlineShellTimeout() { return inlineShellTimeout; }
    public void setInlineShellTimeout(int inlineShellTimeout) { this.inlineShellTimeout = inlineShellTimeout; }
}
