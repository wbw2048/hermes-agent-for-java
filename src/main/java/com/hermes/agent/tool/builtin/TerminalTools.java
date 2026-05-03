package com.hermes.agent.tool.builtin;

import com.hermes.agent.tool.annotation.ToolSet;
import com.hermes.agent.workspace.SessionContext;
import com.hermes.agent.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 终端命令执行工具。
 * <p>
 * 支持在本地执行 shell 命令，带超时保护和危险命令拦截。
 * 命令执行的工作目录锁定在当前会话的 workspace 沙箱内。
 */
@Service
@ToolSet("terminal")
public class TerminalTools {

    private static final Logger log = LoggerFactory.getLogger(TerminalTools.class);

    /**
     * 危险的 shell 命令模式，匹配则拒绝执行。
     */
    private static final Set<String> DANGEROUS_PATTERNS = Set.of(
            "rm -rf /", "rm -rf /*", "rm -rf ~",
            "mkfs", "dd if=", "fdisk", "parted",
            "> /dev/sd", "> /dev/nvme",
            ":(){:|:&};:", "chmod -R 777 /",
            "wget http", "curl http"
    );

    private final long timeoutSeconds;
    private final WorkspaceManager workspaceManager;

    public TerminalTools(
            @Value("${hermes.tools.terminal.timeout-seconds:60}") long timeoutSeconds,
            WorkspaceManager workspaceManager) {
        this.timeoutSeconds = timeoutSeconds;
        this.workspaceManager = workspaceManager;
        log.info("TerminalTools initialized with timeout={}s", timeoutSeconds);
    }

    /**
     * 执行 shell 命令并返回输出。
     *
     * @param command 要执行的 shell 命令
     * @return JSON 格式的执行结果（stdout, stderr, exitCode）
     */
    @Tool(description = "Execute a shell command and return the output. Use for system operations, git commands, package management, etc. Has a timeout and blocks dangerous commands.")
    public String executeCommand(
            @ToolParam(description = "The shell command to execute") String command) {
        log.info("[TOOL-CALL] executeCommand: command='{}'", command);

        if (command == null || command.isBlank()) {
            return "{\"error\": \"Command cannot be empty\"}";
        }

        // 安全检查
        String dangerError = checkDangerousCommand(command);
        if (dangerError != null) {
            return "{\"error\": \"" + escapeJson(dangerError) + "\"}";
        }

        Process process = null;
        try {
            Path cwd = getWorkspaceCwd();
            ProcessBuilder pb = new ProcessBuilder()
                    .command("bash", "-c", command)
                    .directory(cwd.toFile())
                    .redirectErrorStream(false);
            process = pb.start();

            // 读取 stdout 和 stderr
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                outReader.lines().forEach(line -> stdout.append(line).append("\n"));
                errReader.lines().forEach(line -> stderr.append(line).append("\n"));
            }

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return "{\"error\": \"Command timed out after " + timeoutSeconds + " seconds: "
                        + escapeJson(command) + "\"}";
            }

            int exitCode = process.exitValue();
            String out = stdout.toString().stripTrailing();
            String err = stderr.toString().stripTrailing();

            String result = "{\"exit_code\": " + exitCode
                    + ", \"stdout\": \"" + escapeJson(out) + "\""
                    + ", \"stderr\": \"" + escapeJson(err) + "\"}";
            log.info("[TOOL-RETURN] executeCommand: exitCode={}, outputLength={}", exitCode, out.length());
            return result;
        } catch (IOException e) {
            return "{\"error\": \"Failed to execute command: " + escapeJson(e.getMessage()) + "\"}";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return "{\"error\": \"Command execution interrupted\"}";
        }
    }

    private String checkDangerousCommand(String command) {
        String lower = command.toLowerCase().trim();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (lower.contains(pattern)) {
                return "Refusing to execute potentially dangerous command: " + command
                        + ". Blocked pattern: " + pattern;
            }
        }
        return null;
    }

    /**
     * 获取命令执行的工作目录。有会话上下文时返回 workspace root，否则返回当前系统目录。
     */
    private Path getWorkspaceCwd() {
        String sessionId = SessionContext.get();
        if (sessionId != null) {
            Path workspaceRoot = workspaceManager.getWorkspaceRoot(sessionId);
            if (!Files.exists(workspaceRoot)) {
                workspaceManager.createWorkspace(sessionId);
            }
            return workspaceRoot;
        }
        return Path.of(System.getProperty("user.dir"));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
