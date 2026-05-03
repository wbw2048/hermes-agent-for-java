package com.hermes.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务器配置属性。
 * <p>
 * 绑定到 {@code hermes.mcp.servers.*}，每个服务器包含命令、参数、环境变量、超时等。
 */
@ConfigurationProperties(prefix = "hermes.mcp")
public class McpServerProperties {

    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    /**
     * 单个 MCP 服务器的配置。
     */
    public static class ServerConfig {
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private int timeoutSeconds = 120;
        private int connectTimeoutSeconds = 60;
        private boolean enabled = true;
        private List<String> includeTools = new ArrayList<>();
        private List<String> excludeTools = new ArrayList<>();

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getIncludeTools() {
            return includeTools;
        }

        public void setIncludeTools(List<String> includeTools) {
            this.includeTools = includeTools;
        }

        public List<String> getExcludeTools() {
            return excludeTools;
        }

        public void setExcludeTools(List<String> excludeTools) {
            this.excludeTools = excludeTools;
        }
    }
}
