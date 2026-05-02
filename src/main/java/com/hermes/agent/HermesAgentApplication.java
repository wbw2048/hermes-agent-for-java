package com.hermes.agent;

import com.hermes.agent.config.ErrorHandlingProperties;
import com.hermes.agent.config.ToolSetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Hermes Agent Spring Boot 应用入口。
 */
@SpringBootApplication
@EnableConfigurationProperties({ToolSetProperties.class, ErrorHandlingProperties.class})
@EnableRetry
public class HermesAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesAgentApplication.class, args);
    }
}