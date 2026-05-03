package com.hermes.agent.workspace;

import com.hermes.agent.prompt.ContextFileDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 会话工作目录沙箱管理器。
 * <p>
 * 为每个会话创建独立的工作目录，所有文件操作和终端命令严格限制在沙箱内。
 * 对应 Python 的 file_safety.py 中 HERMES_WRITE_SAFE_ROOT 机制。
 */
@Component
public class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);
    private static final String WORKSPACES_DIR = "workspaces";

    /**
     * 为会话创建工作目录。
     *
     * @param sessionId 会话 ID
     * @return workspace 根目录路径
     */
    public Path createWorkspace(String sessionId) {
        Path root = getWorkspaceRoot(sessionId);
        try {
            Files.createDirectories(root);
            log.info("Created workspace for session: {}", sessionId);
        } catch (IOException e) {
            log.error("Failed to create workspace: {}", root, e);
            throw new RuntimeException("Failed to create workspace for session: " + sessionId, e);
        }
        return root;
    }

    /**
     * 将用户输入的路径解析为 workspace 内的绝对路径。
     * <p>
     * 安全策略：
     * <ul>
     *   <li>相对路径：直接解析为 workspace 内的路径</li>
     *   <li>绝对路径：忽略绝对路径前缀，将最后一段路径分量重定向到 workspace 内</li>
     *   <li>~ 路径：展开后重定向到 workspace 内</li>
     *   <li>.. 路径穿越：normalize() 处理后仍会落在 workspace 内</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param userPath  用户输入的路径
     * @return workspace 内的安全绝对路径
     */
    public Path resolvePath(String sessionId, String userPath) {
        Path workspaceRoot = getWorkspaceRoot(sessionId);
        Path resolved;

        if (userPath.startsWith("~")) {
            // ~ 路径：去掉 ~ 前缀，拼接到 workspace 内
            String withoutTilde = userPath.length() > 1 ? userPath.substring(1) : "";
            // 去掉前导 /，防止 workspaceRoot.resolve("/xxx") 返回绝对路径 /xxx
            if (withoutTilde.startsWith("/")) {
                withoutTilde = withoutTilde.substring(1);
            }
            resolved = workspaceRoot.resolve(withoutTilde).normalize();
        } else if (userPath.startsWith("/")) {
            // 绝对路径：normalize 后检查是否逃逸
            Path userResolved = Path.of(userPath).normalize();
            String normalized = userResolved.toString();
            // 如果 normalize 后是 workspace 内的路径，直接使用
            String rootStr = workspaceRoot.toString();
            if (normalized.equals(rootStr) || normalized.startsWith(rootStr + "/")) {
                resolved = userResolved;
            } else {
                // 逃逸路径：取最后一个路径分量，重定向到 workspace 内
                String fileName = userResolved.getFileName() != null ? userResolved.getFileName().toString() : "";
                if (fileName.isEmpty()) {
                    resolved = workspaceRoot;
                } else {
                    resolved = workspaceRoot.resolve(fileName).normalize();
                }
            }
        } else {
            // 相对路径：先解析再 normalize
            resolved = workspaceRoot.resolve(userPath).normalize();
        }

        // 最终安全检查：确保解析后的路径仍在 workspace 内
        String resolvedStr = resolved.toString();
        String rootStr = workspaceRoot.toString();
        if (!resolvedStr.equals(rootStr) && !resolvedStr.startsWith(rootStr + "/")) {
            log.warn("Path escape attempt blocked: sessionId={}, userPath={}, resolved={}", sessionId, userPath, resolved);
            throw new WorkspaceSecurityException("Path escapes workspace boundary: " + userPath);
        }

        return resolved;
    }

    /**
     * 获取会话的 workspace 根目录路径（不检查是否存在）。
     *
     * @param sessionId 会话 ID
     * @return workspace 根目录路径
     */
    public Path getWorkspaceRoot(String sessionId) {
        Path hermesHome = ContextFileDiscovery.resolveHermesHome();
        return hermesHome.resolve(WORKSPACES_DIR).resolve(sessionId);
    }

    /**
     * 删除会话的 workspace 目录。
     *
     * @param sessionId 会话 ID
     */
    public void deleteWorkspace(String sessionId) {
        Path workspaceRoot = getWorkspaceRoot(sessionId);
        if (!Files.exists(workspaceRoot)) {
            log.debug("Workspace already absent: {}", workspaceRoot);
            return;
        }
        try {
            deleteRecursively(workspaceRoot);
            log.info("Deleted workspace for session: {}", sessionId);
        } catch (IOException e) {
            log.error("Failed to delete workspace: {}", workspaceRoot, e);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            Files.delete(path);
            return;
        }
        try (Stream<Path> stream = Files.list(path)) {
            stream.forEach(p -> {
                try {
                    deleteRecursively(p);
                } catch (IOException e) {
                    log.warn("Failed to delete workspace file: {}", p, e);
                }
            });
        }
        Files.delete(path);
    }

    /**
     * Workspace 安全异常。
     */
    public static class WorkspaceSecurityException extends RuntimeException {
        public WorkspaceSecurityException(String message) {
            super(message);
        }
    }
}
