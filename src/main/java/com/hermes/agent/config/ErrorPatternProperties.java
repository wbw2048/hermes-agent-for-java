package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 hermes.agent.error-pattern.* 配置属性。
 */
@ConfigurationProperties(prefix = "hermes.agent.error-pattern")
public class ErrorPatternProperties {

    /** 是否启用错误模式学习 */
    private boolean enabled = true;
    /** 系统提示中注入的最大教训条数 */
    private int maxLessonsInPrompt = 5;
    /** 重复检测时间窗口（小时） */
    private int repeatDetectionWindowHours = 24;
    /** 教训最大长度（字符） */
    private int maxLessonLength = 100;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxLessonsInPrompt() { return maxLessonsInPrompt; }
    public void setMaxLessonsInPrompt(int maxLessonsInPrompt) { this.maxLessonsInPrompt = maxLessonsInPrompt; }
    public int getRepeatDetectionWindowHours() { return repeatDetectionWindowHours; }
    public void setRepeatDetectionWindowHours(int repeatDetectionWindowHours) { this.repeatDetectionWindowHours = repeatDetectionWindowHours; }
    public int getMaxLessonLength() { return maxLessonLength; }
    public void setMaxLessonLength(int maxLessonLength) { this.maxLessonLength = maxLessonLength; }
}
