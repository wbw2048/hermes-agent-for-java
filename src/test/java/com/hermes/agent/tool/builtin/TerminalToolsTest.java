package com.hermes.agent.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.workspace.WorkspaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TerminalTools} 的测试。
 * 覆盖：命令执行、超时、危险命令拦截、错误处理。
 */
class TerminalToolsTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    private TerminalTools createTools() {
        return new TerminalTools(10, new NoOpWorkspaceManager(tempDir));
    }

    @Test
    void executeCommandSimpleEcho() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand("echo hello");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals(0, parsed.get("exit_code"));
        assertTrue(((String) parsed.get("stdout")).contains("hello"));
    }

    @Test
    void executeCommandExitCode() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand("bash -c 'exit 42'");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals(42, parsed.get("exit_code"));
    }

    @Test
    void executeCommandStderr() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand("bash -c 'echo error >&2; exit 1'");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertEquals(1, parsed.get("exit_code"));
        assertTrue(((String) parsed.get("stderr")).contains("error"));
    }

    @Test
    void executeCommandEmptyCommand() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand("");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
    }

    @Test
    void executeCommandNullCommand() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand(null);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
    }

    @Test
    void executeCommandDangerousRmRoot() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand("rm -rf /");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
        assertTrue(((String) parsed.get("error")).contains("dangerous"));
    }

    @Test
    void executeCommandDangerousMkfs() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand("mkfs.ext4 /dev/sda1");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
        assertTrue(((String) parsed.get("error")).contains("dangerous"));
    }

    @Test
    void executeCommandDangerousForkBomb() throws Exception {
        TerminalTools tools = createTools();
        String result = tools.executeCommand(":(){:|:&};:");

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = mapper.readValue(result, Map.class);
        assertTrue(parsed.containsKey("error"));
    }

    @Test
    void toolHasCorrectAnnotation() throws Exception {
        TerminalTools tools = createTools();
        var method = TerminalTools.class.getMethod("executeCommand", String.class);
        var annotation = method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class);
        assertNotNull(annotation);
        assertFalse(annotation.description().isBlank());
    }

    /**
     * 测试用 WorkspaceManager，指向 tempDir。
     */
    private static class NoOpWorkspaceManager extends WorkspaceManager {
        private final Path testRoot;

        NoOpWorkspaceManager(Path testRoot) {
            this.testRoot = testRoot;
        }

        @Override
        public Path getWorkspaceRoot(String sessionId) {
            return testRoot;
        }

        @Override
        public Path createWorkspace(String sessionId) {
            try {
                java.nio.file.Files.createDirectories(testRoot);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            return testRoot;
        }
    }
}
