package com.hermes.agent.controller;

import com.hermes.agent.entity.ErrorPatternEntity;
import com.hermes.agent.repository.ErrorPatternRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ErrorPatternsController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ErrorPatternsControllerTest {

    @Mock
    private ErrorPatternRepository repository;

    private ErrorPatternsController controller;

    @BeforeEach
    void setUp() {
        controller = new ErrorPatternsController(repository);
    }

    @Test
    void listErrors_returnsPaginatedResults() {
        List<ErrorPatternEntity> entities = List.of(createEntity(1L, "file_read", "TOOL_ERROR", false));
        Page<ErrorPatternEntity> page = new PageImpl<>(entities);
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        ResponseEntity<Map<String, Object>> response = controller.listErrors(0, 50);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        assertEquals(1L, body.get("total"));
    }

    @Test
    void getSessionErrors_returnsSessionSpecificErrors() {
        List<ErrorPatternEntity> entities = List.of(
            createEntity(1L, "file_read", "TOOL_ERROR", false)
        );
        when(repository.findBySessionIdOrderByOccurredAtDesc("session-1")).thenReturn(entities);

        ResponseEntity<Map<String, Object>> response = controller.getSessionErrors("session-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("session-1", body.get("sessionId"));
        assertEquals(1, ((List<?>) body.get("errors")).size());
    }

    @Test
    void getStats_aggregatesCorrectly() {
        when(repository.count()).thenReturn(10L);
        when(repository.findRecentLessons(1000)).thenReturn(List.of(createEntityWithLesson(1L)));
        when(repository.countByErrorType()).thenReturn(List.of(
            new Object[]{"TOOL_ERROR", 7L},
            new Object[]{"TIMEOUT", 3L}
        ));

        ResponseEntity<Map<String, Object>> response = controller.getStats();

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(10L, body.get("totalErrors"));
        assertEquals(1L, body.get("totalLessons"));
    }

    @Test
    void listLessons_returnsOnlyLessonsWithContent() {
        List<ErrorPatternEntity> entities = List.of(createEntityWithLesson(1L));
        when(repository.findRecentLessons(50)).thenReturn(entities);

        ResponseEntity<Map<String, Object>> response = controller.listLessons(50);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("success"));
        assertEquals(1, ((List<?>) body.get("lessons")).size());
    }

    @Test
    void toDto_containsAllExpectedFields() {
        ErrorPatternEntity entity = createEntity(42L, "terminal_exec", "TIMEOUT", true);
        entity.setLessonLearned("增加超时时间");
        entity.setErrorSnippet("timeout after 60s");
        entity.setArgumentSummary("(cmd=python)");

        List<ErrorPatternEntity> entities = List.of(entity);
        when(repository.findBySessionIdOrderByOccurredAtDesc("test")).thenReturn(entities);

        ResponseEntity<Map<String, Object>> response = controller.getSessionErrors("test");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertEquals(1, errors.size());

        Map<String, Object> dto = errors.get(0);
        assertEquals(42L, dto.get("id"));
        assertEquals("terminal_exec", dto.get("toolName"));
        assertEquals("TIMEOUT", dto.get("errorType"));
        assertEquals("增加超时时间", dto.get("lessonLearned"));
        assertEquals("timeout after 60s", dto.get("errorSnippet"));
        assertEquals(true, dto.get("repeat"));
    }

    private ErrorPatternEntity createEntity(Long id, String toolName, String errorType, boolean repeat) {
        ErrorPatternEntity e = new ErrorPatternEntity();
        e.setId(id);
        e.setSessionId("test-session");
        e.setToolName(toolName);
        e.setErrorType(errorType);
        e.setErrorSnippet("error details");
        e.setArgumentSummary("(args)");
        e.setOccurredAt(Instant.now());
        e.setRepeat(repeat);
        return e;
    }

    private ErrorPatternEntity createEntityWithLesson(Long id) {
        ErrorPatternEntity e = createEntity(id, "file_read", "TOOL_ERROR", false);
        e.setLessonLearned("测试教训");
        return e;
    }
}
