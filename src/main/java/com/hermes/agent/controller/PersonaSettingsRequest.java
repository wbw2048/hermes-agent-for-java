package com.hermes.agent.controller;

/**
 * 人设设置请求 DTO。
 */
public class PersonaSettingsRequest {

    /** SOUL.md 文件内容 */
    private String content;

    public PersonaSettingsRequest() {}

    public PersonaSettingsRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
