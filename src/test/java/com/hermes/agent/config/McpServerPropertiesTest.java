package com.hermes.agent.config;

import com.hermes.agent.config.McpServerProperties.ServerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpServerPropertiesTest {

    @Test
    void defaultValuesAreSet() {
        ServerConfig config = new ServerConfig();
        assertEquals(120, config.getTimeoutSeconds());
        assertEquals(60, config.getConnectTimeoutSeconds());
        assertTrue(config.isEnabled());
        assertNotNull(config.getArgs());
        assertTrue(config.getArgs().isEmpty());
        assertNotNull(config.getEnv());
        assertTrue(config.getEnv().isEmpty());
        assertNotNull(config.getIncludeTools());
        assertTrue(config.getIncludeTools().isEmpty());
        assertNotNull(config.getExcludeTools());
        assertTrue(config.getExcludeTools().isEmpty());
    }

    @Test
    void settersWork() {
        ServerConfig config = new ServerConfig();
        config.setCommand("npx");
        config.setArgs(List.of("-y", "test"));
        config.setEnv(Map.of("KEY", "value"));
        config.setTimeoutSeconds(30);
        config.setEnabled(false);
        config.setIncludeTools(List.of("tool1"));
        config.setExcludeTools(List.of("tool2"));

        assertEquals("npx", config.getCommand());
        assertEquals(List.of("-y", "test"), config.getArgs());
        assertEquals("value", config.getEnv().get("KEY"));
        assertEquals(30, config.getTimeoutSeconds());
        assertFalse(config.isEnabled());
        assertEquals(List.of("tool1"), config.getIncludeTools());
        assertEquals(List.of("tool2"), config.getExcludeTools());
    }

    @Test
    void propertiesHoldsMultipleServers() {
        McpServerProperties props = new McpServerProperties();
        ServerConfig fs = new ServerConfig();
        fs.setCommand("npx");
        props.getServers().put("filesystem", fs);

        ServerConfig gh = new ServerConfig();
        gh.setCommand("npx");
        props.getServers().put("github", gh);

        assertEquals(2, props.getServers().size());
        assertEquals("npx", props.getServers().get("filesystem").getCommand());
        assertEquals("npx", props.getServers().get("github").getCommand());
    }
}
