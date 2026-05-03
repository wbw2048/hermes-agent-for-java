package com.hermes.agent.service;

import com.hermes.agent.entity.SessionEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 会话导出服务测试。
 */
class SessionExporterTest {

    @Test
    void exportJsonIncludesMetadataAndMessages() {
        SessionStorageService storage = mock(SessionStorageService.class);
        SessionEntity session = new SessionEntity("s1", "Test Chat", Instant.now(), Instant.now());
        session.setMessageCount(2);
        when(storage.getSession("s1")).thenReturn(session);

        List<Message> messages = List.of(
                new UserMessage("Hello"),
                AssistantMessage.builder().content("Hi there!").build()
        );
        when(storage.loadSession("s1")).thenReturn(messages);

        SessionExporter exporter = new SessionExporter(storage);
        String json = exporter.exportJson("s1");

        assertNotNull(json);
        assertTrue(json.contains("sessionId"));
        assertTrue(json.contains("Test Chat"));
        assertTrue(json.contains("user"));
        assertTrue(json.contains("assistant"));
        assertTrue(json.contains("Hello"));
        assertTrue(json.contains("Hi there!"));
    }

    @Test
    void exportMarkdownIncludesHeaders() {
        SessionStorageService storage = mock(SessionStorageService.class);
        SessionEntity session = new SessionEntity("s1", "Test Chat", Instant.now(), Instant.now());
        when(storage.getSession("s1")).thenReturn(session);

        List<Message> messages = List.of(
                new UserMessage("Hello"),
                AssistantMessage.builder().content("Hi there!").build()
        );
        when(storage.loadSession("s1")).thenReturn(messages);

        SessionExporter exporter = new SessionExporter(storage);
        String md = exporter.exportMarkdown("s1");

        assertNotNull(md);
        assertTrue(md.contains("# Test Chat"));
        assertTrue(md.contains("Session ID: s1"));
        assertTrue(md.contains("## User"));
        assertTrue(md.contains("## Assistant"));
        assertTrue(md.contains("Hello"));
        assertTrue(md.contains("Hi there!"));
    }

    @Test
    void exportThrowsForUnknownSession() {
        SessionStorageService storage = mock(SessionStorageService.class);
        when(storage.getSession("unknown")).thenReturn(null);

        SessionExporter exporter = new SessionExporter(storage);

        assertThrows(IllegalArgumentException.class, () -> exporter.exportJson("unknown"));
        assertThrows(IllegalArgumentException.class, () -> exporter.exportMarkdown("unknown"));
    }

    @Test
    void exportJsonWithUntitledSession() {
        SessionStorageService storage = mock(SessionStorageService.class);
        SessionEntity session = new SessionEntity("s1", null, Instant.now(), Instant.now());
        when(storage.getSession("s1")).thenReturn(session);
        when(storage.loadSession("s1")).thenReturn(List.of());

        SessionExporter exporter = new SessionExporter(storage);
        String json = exporter.exportJson("s1");

        assertNotNull(json);
        // Jackson serializes null as "title":null
        assertTrue(json.contains("title"));
    }

    @Test
    void exportMarkdownWithUntitledSession() {
        SessionStorageService storage = mock(SessionStorageService.class);
        SessionEntity session = new SessionEntity("s1", null, Instant.now(), Instant.now());
        when(storage.getSession("s1")).thenReturn(session);
        when(storage.loadSession("s1")).thenReturn(List.of());

        SessionExporter exporter = new SessionExporter(storage);
        String md = exporter.exportMarkdown("s1");

        assertNotNull(md);
        assertTrue(md.contains("# Untitled"));
    }
}
