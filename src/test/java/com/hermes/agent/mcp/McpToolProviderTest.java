package com.hermes.agent.mcp;

import com.hermes.agent.config.McpServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpToolProviderTest {

    @Mock
    private McpConnectionManager connectionManager;

    private McpServerProperties properties;
    private McpToolExecutor toolExecutor;
    private McpToolProvider provider;

    @BeforeEach
    void setUp() {
        properties = new McpServerProperties();
        toolExecutor = new McpToolExecutor(connectionManager);
        provider = new McpToolProvider(connectionManager, properties, toolExecutor);
    }

    @Test
    void discoverAllToolsReturnsEmptyWhenNoServers() {
        assertTrue(provider.discoverAllTools().isEmpty());
    }

    @Test
    void discoverAllToolsReturnsEmptyWhenServerNotConnected() {
        McpServerProperties.ServerConfig config = new McpServerProperties.ServerConfig();
        config.setCommand("npx");
        config.setEnabled(true);
        properties.getServers().put("test", config);

        when(connectionManager.getClient("test")).thenReturn(null);

        assertTrue(provider.discoverAllTools().isEmpty());
    }

    @Test
    void shouldIncludeToolRespectsIncludeList() {
        McpServerProperties.ServerConfig config = new McpServerProperties.ServerConfig();
        config.setIncludeTools(List.of("allowed"));
        properties.getServers().put("srv", config);

        assertTrue(provider.shouldIncludeTool("srv", "allowed"));
        assertFalse(provider.shouldIncludeTool("srv", "blocked"));
    }

    @Test
    void shouldIncludeToolRespectsExcludeList() {
        McpServerProperties.ServerConfig config = new McpServerProperties.ServerConfig();
        config.setExcludeTools(List.of("bad"));
        properties.getServers().put("srv", config);

        assertFalse(provider.shouldIncludeTool("srv", "bad"));
        assertTrue(provider.shouldIncludeTool("srv", "good"));
    }

    @Test
    void buildCallbackNameIsSanitized() {
        String name = McpToolProvider.buildCallbackName("my server", "tool/name");
        assertEquals("mcp_my_server_tool_name", name);
    }
}
