package com.hermes.agent.tool.builtin;

import com.hermes.agent.workspace.SessionContext;
import com.hermes.agent.workspace.WorkspaceManager;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileTools 沙箱测试 — 验证文件操作被限制在 workspace 内。
 */
class FileToolsSandboxTest {

    private TestableWorkspaceManager workspaceManager;
    private FileTools fileTools;
    private Path testRoot;

    @BeforeEach
    void setUp() throws IOException {
        testRoot = Files.createTempDirectory("hermes-filetools-test-");
        workspaceManager = new TestableWorkspaceManager(testRoot);
        fileTools = new FileTools(workspaceManager);
    }

    @AfterEach
    void tearDown() {
        SessionContext.clear();
        try {
            deleteRecursively(testRoot);
        } catch (IOException e) {
            // ignore
        }
    }

    @Test
    @DisplayName("写入文件到 workspace 内")
    void writeFileInWorkspace() throws Exception {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        String result = fileTools.writeFile("test.txt", "hello world");
        assertTrue(result.contains("\"success\": true"));

        Path workspaceRoot = workspaceManager.getWorkspaceRoot("sess-001");
        Path file = workspaceRoot.resolve("test.txt");
        assertTrue(Files.exists(file));
        assertEquals("hello world", Files.readString(file));
    }

    @Test
    @DisplayName("从 workspace 内读取文件")
    void readFileInWorkspace() throws IOException {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        Path workspaceRoot = workspaceManager.getWorkspaceRoot("sess-001");
        Files.writeString(workspaceRoot.resolve("readme.txt"), "line1\nline2\nline3");

        String result = fileTools.readFile("readme.txt", 1, 10);
        assertTrue(result.contains("line1"));
        assertTrue(result.contains("line2"));
        assertTrue(result.contains("line3"));
    }

    @Test
    @DisplayName("绝对路径写入被重定向到 workspace 内")
    void absolutePathRedirected() throws Exception {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        // 尝试写入 /tmp/test-sandbox.txt — 应被重定向到 workspace 内
        String result = fileTools.writeFile("/tmp/test-sandbox.txt", "should be in workspace");

        assertTrue(result.contains("\"success\": true"));

        // 验证文件不在 /tmp 而在 workspace 内
        Path workspaceRoot = workspaceManager.getWorkspaceRoot("sess-001");
        Path redirected = workspaceRoot.resolve("test-sandbox.txt");
        assertTrue(Files.exists(redirected));
        assertEquals("should be in workspace", Files.readString(redirected));
    }

    @Test
    @DisplayName(".. 路径穿越不逃逸 workspace — 被拒绝")
    void pathTraversalContained() {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        // ../../../etc/evil.txt 作为相对路径解析后 normalize 会逃逸出 workspace
        // FileTools 捕获异常后返回错误 JSON
        String result = fileTools.writeFile("../../../etc/evil.txt", "should not escape");
        assertTrue(result.contains("\"error\":"));
        assertTrue(result.contains("escapes workspace boundary"));
    }

    @Test
    @DisplayName("~ 路径被重定向到 workspace 内")
    void tildePathRedirected() throws Exception {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        String result = fileTools.writeFile("~/.secret", "data");
        assertTrue(result.contains("\"success\": true"));

        Path workspaceRoot = workspaceManager.getWorkspaceRoot("sess-001");
        Path file = workspaceRoot.resolve(".secret");
        assertTrue(Files.exists(file));
    }

    @Test
    @DisplayName("无会话上下文时回退到旧行为")
    void noSessionFallback() throws IOException {
        // 未设置 SessionContext
        String result = fileTools.writeFile(testRoot.resolve("fallback.txt").toString(), "fallback data");
        assertTrue(result.contains("\"success\": true"));
        assertTrue(Files.exists(testRoot.resolve("fallback.txt")));
    }

    @Test
    @DisplayName("patch 操作限制在 workspace 内")
    void patchContained() throws IOException {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        Path workspaceRoot = workspaceManager.getWorkspaceRoot("sess-001");
        Files.writeString(workspaceRoot.resolve("patch.txt"), "hello world");

        String result = fileTools.patch("patch.txt", "world", "there", false);
        assertTrue(result.contains("\"success\": true"));
        assertEquals("hello there", Files.readString(workspaceRoot.resolve("patch.txt")));
    }

    @Test
    @DisplayName("searchFiles 限制在 workspace 内")
    void searchFilesContained() throws IOException {
        workspaceManager.createWorkspace("sess-001");
        SessionContext.set("sess-001");

        Path workspaceRoot = workspaceManager.getWorkspaceRoot("sess-001");
        Files.writeString(workspaceRoot.resolve("search.txt"), "find me here");

        String result = fileTools.searchFiles("find me", "content", ".", 10);
        assertTrue(result.contains("search.txt"));
    }

    // --- Testable subclass ---

    private static class TestableWorkspaceManager extends WorkspaceManager {
        private final Path hermesHome;

        TestableWorkspaceManager(Path hermesHome) {
            this.hermesHome = hermesHome;
        }

        @Override
        public Path getWorkspaceRoot(String sessionId) {
            return hermesHome.resolve("workspaces").resolve(sessionId);
        }
    }

    // --- Helpers ---

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            Files.delete(path);
            return;
        }
        try (var stream = Files.list(path)) {
            stream.forEach(p -> {
                try { deleteRecursively(p); } catch (IOException e) { /* ignore */ }
            });
        }
        Files.delete(path);
    }
}
