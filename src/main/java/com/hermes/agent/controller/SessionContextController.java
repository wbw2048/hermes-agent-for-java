package com.hermes.agent.controller;

import com.hermes.agent.prompt.ContextFileDiscovery;
import com.hermes.agent.prompt.PromptInjectionDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 会话上下文文件管理 REST API。
 * 提供对会话级上下文文件（CLAUDE.md、AGENTS.md、.hermes.md 等）的 CRUD 操作。
 */
@RestController
@RequestMapping("/api/conversations/{sessionId}/context")
@CrossOrigin(origins = "*")
public class SessionContextController {

    private static final Logger log = LoggerFactory.getLogger(SessionContextController.class);
    private static final PromptInjectionDetector detector = new PromptInjectionDetector();
    private static final int MAX_CONTENT_LENGTH = 20_000;

    private static final Set<String> ALLOWED_FILES = Set.of(
        ".hermes.md", "HERMES.md",
        "AGENTS.md", "agents.md",
        "CLAUDE.md", "claude.md",
        ".cursorrules"
    );

    /**
     * 列出会话目录下所有已存在的上下文文件。
     * GET /api/conversations/{sessionId}/context
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listContextFiles(@PathVariable String sessionId) {
        Path sessionDir = ContextFileDiscovery.resolveSessionContextDir(sessionId, false);
        if (sessionDir == null || !Files.isDirectory(sessionDir)) {
            return ResponseEntity.ok(Map.of("files", Collections.emptyList()));
        }

        List<Map<String, String>> files = new ArrayList<>();
        try (var stream = Files.list(sessionDir)) {
            stream.filter(p -> ALLOWED_FILES.contains(p.getFileName().toString()))
                .sorted()
                .forEach(p -> {
                    try {
                        files.add(Map.of(
                            "name", p.getFileName().toString(),
                            "size", String.valueOf(Files.size(p))
                        ));
                    } catch (IOException e) {
                        // skip
                    }
                });
        } catch (IOException e) {
            log.warn("Could not list session context dir: {}", sessionDir);
            return ResponseEntity.internalServerError().body(Map.of("error", "读取失败"));
        }
        return ResponseEntity.ok(Map.of("files", files));
    }

    /**
     * 读取指定上下文文件内容。
     * GET /api/conversations/{sessionId}/context/{fileName}
     */
    @GetMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> getContextFile(
            @PathVariable String sessionId,
            @PathVariable String fileName
    ) {
        if (!ALLOWED_FILES.contains(fileName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的文件: " + fileName));
        }

        Path sessionDir = ContextFileDiscovery.resolveSessionContextDir(sessionId, false);
        if (sessionDir == null || !Files.isDirectory(sessionDir)) {
            return ResponseEntity.ok(Map.of("exists", false, "content", ""));
        }

        Path filePath = sessionDir.resolve(fileName);
        if (!Files.isRegularFile(filePath)) {
            return ResponseEntity.ok(Map.of("exists", false, "content", ""));
        }

        try {
            String content = Files.readString(filePath);
            return ResponseEntity.ok(Map.of("exists", true, "content", content));
        } catch (IOException e) {
            log.error("Failed to read {}: {}", fileName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "读取失败"));
        }
    }

    /**
     * 写入/更新上下文文件。
     * PUT /api/conversations/{sessionId}/context/{fileName}
     * Body: { "content": "..." }
     */
    @PutMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> setContextFile(
            @PathVariable String sessionId,
            @PathVariable String fileName,
            @RequestBody Map<String, String> request
    ) {
        if (!ALLOWED_FILES.contains(fileName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的文件: " + fileName));
        }

        String content = request.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能为空"));
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error", "内容不能超过 20000 字符"));
        }

        // 注入防护扫描
        String sanitized = detector.scanAndSanitize(content, fileName);
        if (sanitized.startsWith("[BLOCKED:")) {
            return ResponseEntity.badRequest().body(Map.of("error", sanitized));
        }

        try {
            Path sessionDir = ContextFileDiscovery.resolveSessionContextDir(sessionId, true);
            if (sessionDir == null) {
                return ResponseEntity.internalServerError().body(Map.of("error", "无法创建会话上下文目录"));
            }
            Path filePath = sessionDir.resolve(fileName);
            Files.writeString(filePath, content.strip());
            log.info("Context file saved: {}/{}", sessionId, fileName);
            return ResponseEntity.ok(Map.of("success", true, "message", fileName + " 已保存"));
        } catch (IOException e) {
            log.error("Failed to write {}: {}", fileName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "保存失败: " + e.getMessage()));
        }
    }

    /**
     * 删除上下文文件。
     * DELETE /api/conversations/{sessionId}/context/{fileName}
     */
    @DeleteMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteContextFile(
            @PathVariable String sessionId,
            @PathVariable String fileName
    ) {
        if (!ALLOWED_FILES.contains(fileName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的文件: " + fileName));
        }

        Path sessionDir = ContextFileDiscovery.resolveSessionContextDir(sessionId, false);
        if (sessionDir == null || !Files.isDirectory(sessionDir)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "文件不存在"));
        }

        Path filePath = sessionDir.resolve(fileName);
        if (!Files.isRegularFile(filePath)) {
            return ResponseEntity.ok(Map.of("success", true, "message", "文件不存在"));
        }

        try {
            Files.delete(filePath);
            log.info("Context file deleted: {}/{}", sessionId, fileName);
            return ResponseEntity.ok(Map.of("success", true, "message", fileName + " 已删除"));
        } catch (IOException e) {
            log.error("Failed to delete {}: {}", fileName, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "删除失败"));
        }
    }
}
