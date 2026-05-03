package com.hermes.agent.config;

import com.hermes.agent.skill.SkillProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Skills 系统 Spring 配置类。
 * 启用 SkillProperties 配置绑定。
 */
@Configuration
@EnableConfigurationProperties(SkillProperties.class)
public class SkillConfig {
}
