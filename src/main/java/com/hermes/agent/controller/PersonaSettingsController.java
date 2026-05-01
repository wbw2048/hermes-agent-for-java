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
import java.util.Map;

/**
 * 人设（SOUL.md）设置 API。
 */
@RestController
@RequestMapping("/api/settings")
public class PersonaSettingsController {

    private static final Logger log = LoggerFactory.getLogger(PersonaSettingsController.class);

    private final PromptInjectionDetector detector = new PromptInjectionDetector();

    /**
     * 读取当前 SOUL.md 内容。
     */
    @GetMapping("/persona")
    public ResponseEntity<Map<String, Object>> getPersona() {
        Path hermesHome = ContextFileDiscovery.resolveHermesHome();
        Path soulPath = hermesHome.resolve("SOUL.md");

        if (!Files.isRegularFile(soulPath)) {
            return ResponseEntity.ok(Map.of(
                "exists", false,
                "content", ""
            ));
        }

        try {
            String content = Files.readString(soulPath);
            return ResponseEntity.ok(Map.of(
                "exists", true,
                "content", content
            ));
        } catch (IOException e) {
            log.error("Failed to read SOUL.md: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "读取 SOUL.md 失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 写入 SOUL.md。
     */
    private static final int MAX_CONTENT_LENGTH = 20_000;

    @PutMapping("/persona")
    public ResponseEntity<Map<String, Object>> setPersona(@RequestBody PersonaSettingsRequest request) {
        if (request.getContent() == null || request.getContent().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "人设内容不能为空"
            ));
        }
        if (request.getContent().length() > MAX_CONTENT_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "人设内容不能超过 20000 字符"
            ));
        }

        // 注入防护扫描
        String sanitized = detector.scanAndSanitize(request.getContent(), "SOUL.md");
        if (sanitized.startsWith("[BLOCKED:")) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", sanitized
            ));
        }

        try {
            Path hermesHome = ContextFileDiscovery.resolveHermesHome();
            if (!Files.isDirectory(hermesHome)) {
                Files.createDirectories(hermesHome);
            }
            Path soulPath = hermesHome.resolve("SOUL.md");
            Files.writeString(soulPath, request.getContent().strip());
            log.info("SOUL.md updated");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "人设已保存"
            ));
        } catch (IOException e) {
            log.error("Failed to write SOUL.md: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "保存 SOUL.md 失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 删除 SOUL.md，恢复默认身份。
     */
    @DeleteMapping("/persona")
    public ResponseEntity<Map<String, Object>> deletePersona() {
        Path hermesHome = ContextFileDiscovery.resolveHermesHome();
        Path soulPath = hermesHome.resolve("SOUL.md");

        if (!Files.isRegularFile(soulPath)) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "人设文件不存在"
            ));
        }

        try {
            Files.delete(soulPath);
            log.info("SOUL.md deleted");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "人设已恢复为默认"
            ));
        } catch (IOException e) {
            log.error("Failed to delete SOUL.md: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "删除 SOUL.md 失败: " + e.getMessage()
            ));
        }
    }
}
