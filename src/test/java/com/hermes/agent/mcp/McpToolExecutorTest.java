package com.hermes.agent.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpToolExecutorTest {

    @Test
    void sanitizeCredentialsRemovesSecrets() {
        String input = "Error with token=abc123 and sk-key123 and Bearer xyz";
        String result = McpToolExecutor.sanitizeCredentials(input);
        assertFalse(result.contains("abc123"));
        assertFalse(result.contains("sk-key123"));
        assertFalse(result.contains("xyz"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void sanitizeCredentialsHandlesNull() {
        assertNull(McpToolExecutor.sanitizeCredentials(null));
    }

    @Test
    void sanitizeCredentialsLeavesCleanText() {
        String input = "File not found: /tmp/test.txt";
        String result = McpToolExecutor.sanitizeCredentials(input);
        assertEquals("File not found: /tmp/test.txt", result);
    }

    @Test
    void sanitizeCredentialsStripsGitHubToken() {
        String input = "Auth failed: ghp_abcdefghijklmnopqrstuvwxyz";
        String result = McpToolExecutor.sanitizeCredentials(input);
        assertFalse(result.contains("ghp_"));
        assertTrue(result.contains("[REDACTED]"));
    }

    @Test
    void sanitizeCredentialsStripsAPIKey() {
        String input = "API_KEY=supersecretkey123 denied";
        String result = McpToolExecutor.sanitizeCredentials(input);
        assertFalse(result.contains("supersecretkey123"));
    }
}
