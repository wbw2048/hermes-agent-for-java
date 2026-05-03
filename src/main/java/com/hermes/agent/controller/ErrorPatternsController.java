package com.hermes.agent.controller;

import com.hermes.agent.entity.ErrorPatternEntity;
import com.hermes.agent.repository.ErrorPatternRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 错误模式管理控制器。
 * 提供查看历史错误模式和已积累经验教训的 REST API。
 */
@RestController
@RequestMapping("/api/error-patterns")
@CrossOrigin(origins = "*")
public class ErrorPatternsController {

    private final ErrorPatternRepository repository;

    public ErrorPatternsController(ErrorPatternRepository repository) {
        this.repository = repository;
    }

    /**
     * 分页获取所有历史错误模式。
     * GET /api/error-patterns?page=0&size=50
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listErrors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<ErrorPatternEntity> result = repository.findAll(pageRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("errors", result.getContent().stream().map(this::toDto).toList());
        response.put("total", result.getTotalElements());
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取指定会话的错误模式。
     * GET /api/error-patterns/session/{sessionId}
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionErrors(@PathVariable String sessionId) {
        List<ErrorPatternEntity> errors = repository.findBySessionIdOrderByOccurredAtDesc(sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("errors", errors.stream().map(this::toDto).toList());
        response.put("total", errors.size());
        return ResponseEntity.ok(response);
    }

    /**
     * 获取统计信息（各错误类型的数量）。
     * GET /api/error-patterns/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalErrors = repository.count();
        long totalLessons = repository.findRecentLessons(1000).size();
        long repeatErrors = repository.findAll().stream().filter(ErrorPatternEntity::isRepeat).count();

        List<Object[]> byType = repository.countByErrorType();
        Map<String, Long> typeCounts = new HashMap<>();
        for (Object[] row : byType) {
            typeCounts.put((String) row[0], (Long) row[1]);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("totalErrors", totalErrors);
        response.put("totalLessons", totalLessons);
        response.put("repeatErrors", repeatErrors);
        response.put("byErrorType", typeCounts);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取所有已提取的教训列表。
     * GET /api/error-patterns/lessons
     */
    @GetMapping("/lessons")
    public ResponseEntity<Map<String, Object>> listLessons(
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<ErrorPatternEntity> entries = repository.findRecentLessons(limit);

        List<Map<String, Object>> lessons = entries.stream().map(e -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", e.getId());
            m.put("toolName", e.getToolName());
            m.put("lesson", e.getLessonLearned());
            m.put("errorType", e.getErrorType());
            m.put("occurredAt", e.getOccurredAt().toString());
            m.put("repeat", e.isRepeat());
            return m;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("lessons", lessons);
        response.put("total", lessons.size());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toDto(ErrorPatternEntity e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId());
        m.put("sessionId", e.getSessionId());
        m.put("toolName", e.getToolName());
        m.put("argumentSummary", e.getArgumentSummary());
        m.put("errorType", e.getErrorType());
        m.put("errorSnippet", e.getErrorSnippet());
        m.put("occurredAt", e.getOccurredAt().toString());
        m.put("lessonLearned", e.getLessonLearned());
        m.put("repeat", e.isRepeat());
        return m;
    }
}
