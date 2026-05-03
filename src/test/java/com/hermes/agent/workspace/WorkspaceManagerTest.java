package com.hermes.agent.workspace;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkspaceManager 单元测试。
 */
class WorkspaceManagerTest {

    private TestableWorkspaceManager manager;
    private Path testRoot;

    @BeforeEach
    void setUp() throws IOException {
        testRoot = Files.createTempDirectory("hermes-workspace-test-");
        manager = new TestableWorkspaceManager(testRoot);
    }

    @AfterEach
    void tearDown() {
        try {
            deleteRecursively(testRoot);
        } catch (IOException e) {
            // ignore
        }
    }

    @Test
    @DisplayName("创建 workspace 目录")
    void createWorkspace() {
        Path root = manager.createWorkspace("sess-001");
        assertTrue(Files.isDirectory(root));
        assertTrue(root.toString().contains("workspaces"));
        assertTrue(root.toString().contains("sess-001"));
    }

    @Test
    @DisplayName("重复创建 workspace 不报错")
    void createWorkspaceTwice() {
        Path root1 = manager.createWorkspace("sess-001");
        Path root2 = manager.createWorkspace("sess-001");
        assertEquals(root1, root2);
        assertTrue(Files.isDirectory(root1));
    }

    @Test
    @DisplayName("解析相对路径到 workspace 内")
    void resolveRelativePath() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "src/main.java");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        assertEquals(root.resolve("src/main.java").normalize(), resolved);
    }

    @Test
    @DisplayName("解析绝对路径 — 重定向到 workspace 内")
    void resolveAbsolutePath() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "/etc/passwd");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        // 绝对路径 /etc/passwd 被重定向为 workspace/passwd
        assertEquals(root.resolve("passwd"), resolved);
    }

    @Test
    @DisplayName("解析根目录 / — 返回 workspace root")
    void resolveRootDirectory() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "/");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertEquals(root, resolved);
    }

    @Test
    @DisplayName("解析 ~ 路径 — 重定向到 workspace 内")
    void resolveTildePath() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "~/.bashrc");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        assertEquals(root.resolve(".bashrc"), resolved);
    }

    @Test
    @DisplayName("解析 ~/sub/path 路径")
    void resolveTildeSubPath() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "~/foo/bar.txt");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        assertEquals(root.resolve("foo/bar.txt"), resolved);
    }

    @Test
    @DisplayName("解析单独的 ~ 路径")
    void resolveTildeOnly() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "~");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertEquals(root, resolved);
    }

    @Test
    @DisplayName(".. 路径穿越 — 相对路径逃逸被拒绝")
    void blockPathTraversal() {
        manager.createWorkspace("sess-001");
        // foo/../../../etc/passwd 作为相对路径解析后 normalize 会逃逸出 workspace
        // 应该抛出 WorkspaceSecurityException
        assertThrows(WorkspaceManager.WorkspaceSecurityException.class, () -> {
            manager.resolvePath("sess-001", "foo/../../../etc/passwd");
        });
    }

    @Test
    @DisplayName("绝对路径逃逸被重定向到 workspace 内")
    void absolutePathRedirected() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "/etc/passwd");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        // 被重定向为 workspace/passwd
        assertEquals(root.resolve("passwd"), resolved);
    }

    @Test
    @DisplayName("深层 .. 逃逸被阻止")
    void deepPathEscapeBlocked() {
        manager.createWorkspace("sess-001");
        // 构造一个 normalize 后仍在 workspace 内的路径
        Path resolved = manager.resolvePath("sess-001", "a/b/c/../../d");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        assertEquals(root.resolve("a/d"), resolved);
    }

    @Test
    @DisplayName("删除 workspace 目录")
    void deleteWorkspace() {
        Path root = manager.createWorkspace("sess-001");
        assertTrue(Files.exists(root));
        manager.deleteWorkspace("sess-001");
        assertFalse(Files.exists(root));
    }

    @Test
    @DisplayName("删除含文件的 workspace 目录")
    void deleteWorkspaceWithFiles() throws IOException {
        Path root = manager.createWorkspace("sess-001");
        Files.writeString(root.resolve("test.txt"), "hello");
        Path subDir = root.resolve("sub");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("nested.txt"), "world");

        manager.deleteWorkspace("sess-001");
        assertFalse(Files.exists(root));
    }

    @Test
    @DisplayName("删除不存在的 workspace 不报错")
    void deleteNonExistentWorkspace() {
        assertDoesNotThrow(() -> manager.deleteWorkspace("nonexistent"));
    }

    @Test
    @DisplayName("workspace 内创建子目录和文件")
    void createFilesInWorkspace() throws IOException {
        Path root = manager.createWorkspace("sess-001");
        Path subDir = root.resolve("src");
        Files.createDirectories(subDir);
        Path file = subDir.resolve("test.java");
        Files.writeString(file, "hello");
        assertTrue(Files.exists(file));
        assertEquals("hello", Files.readString(file));
    }

    @Test
    @DisplayName("相对路径含 .. 被正确解析")
    void relativePathWithDots() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "src/test/../main.java");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertTrue(resolved.startsWith(root));
        assertEquals(root.resolve("src/main.java"), resolved);
    }

    @Test
    @DisplayName("空路径解析返回 workspace root")
    void resolveEmptyPath() {
        manager.createWorkspace("sess-001");
        Path resolved = manager.resolvePath("sess-001", "");
        Path root = manager.getWorkspaceRoot("sess-001");
        assertEquals(root, resolved);
    }

    // --- Testable subclass that uses a custom root instead of HERMES_HOME ---

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
