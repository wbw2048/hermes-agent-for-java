package com.hermes.agent.controller;

import com.hermes.agent.workspace.WorkspaceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 会话工作目录 REST API。
 */
@RestController
@RequestMapping("/api/conversations/{sessionId}/workspace")
@CrossOrigin(origins = "*")
public class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final WorkspaceManager workspaceManager;

    public WorkspaceController(WorkspaceManager workspaceManager) {
        this.workspaceManager = workspaceManager;
    }

    /**
     * 获取会话工作目录信息。
     * GET /api/conversations/{sessionId}/workspace
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getWorkspaceInfo(@PathVariable String sessionId) {
        try {
            Path workspaceRoot = workspaceManager.getWorkspaceRoot(sessionId);
            boolean exists = Files.exists(workspaceRoot);

            Map<String, Object> result = Map.of(
                "path", workspaceRoot.toString(),
                "exists", exists
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get workspace info for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "获取工作目录信息失败"));
        }
    }

    /**
     * 列出工作目录下的文件。
     * GET /api/conversations/{sessionId}/workspace/files?path=.
     */
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listWorkspaceFiles(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = ".") String path) {
        try {
            Path resolved = workspaceManager.resolvePath(sessionId, path);
            if (!Files.exists(resolved)) {
                return ResponseEntity.ok(Map.of("files", List.of(), "path", resolved.toString()));
            }
            if (!Files.isDirectory(resolved)) {
                return ResponseEntity.badRequest().body(Map.of("error", "路径不是目录: " + path));
            }

            List<Map<String, String>> files = new ArrayList<>();
            try (Stream<Path> stream = Files.list(resolved)) {
                stream.sorted()
                    .forEach(p -> {
                        try {
                            String relPath = resolved.relativize(p).toString();
                            files.add(Map.of(
                                "name", p.getFileName().toString(),
                                "path", relPath,
                                "isDirectory", String.valueOf(Files.isDirectory(p)),
                                "size", String.valueOf(Files.size(p))
                            ));
                        } catch (IOException e) {
                            // skip
                        }
                    });
            }

            return ResponseEntity.ok(Map.of("files", files, "path", resolved.toString()));
        } catch (Exception e) {
            log.error("Failed to list workspace files for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "列出文件失败"));
        }
    }
}
