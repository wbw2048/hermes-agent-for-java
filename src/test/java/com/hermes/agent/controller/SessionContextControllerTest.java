package com.hermes.agent.controller;

import com.hermes.agent.prompt.ContextFileDiscovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SessionContextController} 的测试。
 */
class SessionContextControllerTest {

    private SessionContextController controller;
    private String sessionId;
    private Path hermesHome;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        controller = new SessionContextController();
        sessionId = "test-session-001";
        // We can't override HERMES_HOME env var, so we test via the actual filesystem
        // using the default ~/.hermes/contexts/ path. To avoid polluting real dirs,
        // we verify ALLOWED_FILES validation and error paths that don't need real dirs.
    }

    @AfterEach
    void tearDown() {
        // Clean up test session context dir if created
        Path sessionDir = ContextFileDiscovery.resolveSessionContextDir(sessionId, false);
        if (sessionDir != null && Files.isDirectory(sessionDir)) {
            deleteDir(sessionDir);
        }
    }

    @Test
    void getContextFileReturnsEmptyWhenNoDir() {
        ResponseEntity<Map<String, Object>> response = controller.getContextFile(sessionId, "CLAUDE.md");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(false, body.get("exists"));
        assertEquals("", body.get("content"));
    }

    @Test
    void setAndGetContextFile() throws IOException {
        ResponseEntity<Map<String, Object>> setResponse = controller.setContextFile(
            sessionId, "CLAUDE.md", Map.of("content", "# Test Project\nUse Gradle."));
        assertEquals(HttpStatus.OK, setResponse.getStatusCode());
        assertTrue((Boolean) setResponse.getBody().get("success"));

        ResponseEntity<Map<String, Object>> getResponse = controller.getContextFile(sessionId, "CLAUDE.md");
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        Map<String, Object> body = getResponse.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("exists"));
        assertEquals("# Test Project\nUse Gradle.", body.get("content"));
    }

    @Test
    void deleteContextFile() throws IOException {
        controller.setContextFile(sessionId, "AGENTS.md", Map.of("content", "# Agents"));

        ResponseEntity<Map<String, Object>> deleteResponse = controller.deleteContextFile(sessionId, "AGENTS.md");
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertTrue((Boolean) deleteResponse.getBody().get("success"));

        // Verify file is gone
        ResponseEntity<Map<String, Object>> getResponse = controller.getContextFile(sessionId, "AGENTS.md");
        assertEquals(false, getResponse.getBody().get("exists"));
    }

    @Test
    void listContextFiles() throws IOException {
        controller.setContextFile(sessionId, "CLAUDE.md", Map.of("content", "# A"));
        controller.setContextFile(sessionId, "AGENTS.md", Map.of("content", "# B"));

        ResponseEntity<Map<String, Object>> response = controller.listContextFiles(sessionId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> files = (List<Map<String, String>>) body.get("files");
        assertEquals(2, files.size());
    }

    @Test
    void rejectsDisallowedFileName() {
        ResponseEntity<Map<String, Object>> response = controller.getContextFile(sessionId, "pom.xml");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").toString().contains("不支持的文件"));
    }

    @Test
    void rejectsEmptyContent() {
        ResponseEntity<Map<String, Object>> response = controller.setContextFile(sessionId, "CLAUDE.md", Map.of("content", ""));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void rejectsTooLongContent() {
        ResponseEntity<Map<String, Object>> response = controller.setContextFile(
            sessionId, "CLAUDE.md", Map.of("content", "X".repeat(20_001)));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void deleteNonExistentFileReturnsSuccess() {
        ResponseEntity<Map<String, Object>> response = controller.deleteContextFile(sessionId, "CLAUDE.md");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue((Boolean) response.getBody().get("success"));
    }

    @Test
    void listEmptySessionReturnsEmptyList() {
        ResponseEntity<Map<String, Object>> response = controller.listContextFiles(sessionId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<?> files = (List<?>) body.get("files");
        assertTrue(files.isEmpty());
    }

    private void deleteDir(Path dir) {
        try {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); }
                    catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }
}
