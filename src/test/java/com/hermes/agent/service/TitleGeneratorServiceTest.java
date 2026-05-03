package com.hermes.agent.service;

import com.hermes.agent.config.TitleGenerationProperties;
import com.hermes.agent.entity.SessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 标题自动生成服务测试。
 */
class TitleGeneratorServiceTest {

    private TitleGenerationProperties properties;
    private ChatClient.Builder chatClientBuilder;
    private SessionStorageService sessionStorageService;

    @BeforeEach
    void setUp() {
        properties = new TitleGenerationProperties();
        chatClientBuilder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);
        sessionStorageService = mock(SessionStorageService.class);
    }

    @Test
    void disabledSkipsGeneration() {
        properties.setEnabled(false);
        TitleGeneratorService service = new TitleGeneratorService(
                chatClientBuilder, properties, sessionStorageService
        );

        // Should not call LLM or update session title when disabled
        assertDoesNotThrow(() -> service.generateTitleAsync("sess-1", "hello", "world"));
        verify(sessionStorageService, never()).updateSessionTitle(anyString(), anyString());
    }

    @Test
    void existingTitleSkipsGeneration() {
        SessionEntity session = new SessionEntity("sess-1", "My Title", Instant.now(), Instant.now());
        when(sessionStorageService.getSession("sess-1")).thenReturn(session);

        TitleGeneratorService service = new TitleGeneratorService(
                chatClientBuilder, properties, sessionStorageService
        );

        assertDoesNotThrow(() -> service.generateTitleAsync("sess-1", "hello", "world"));
        verify(sessionStorageService, never()).updateSessionTitle(anyString(), anyString());
    }

    @Test
    void noSessionDoesNotThrow() {
        when(sessionStorageService.getSession("sess-1")).thenReturn(null);

        TitleGeneratorService service = new TitleGeneratorService(
                chatClientBuilder, properties, sessionStorageService
        );

        // LLM call will fail since mock returns null, but should not throw
        assertDoesNotThrow(() -> service.generateTitleAsync("sess-1", "hello", "world"));
    }

    @Test
    void propertiesDefaults() {
        assertTrue(properties.isEnabled());
        assertEquals(30, properties.getTimeoutSeconds());
        assertEquals(80, properties.getMaxLength());
        assertEquals(500, properties.getSnippetLength());
    }

    @Test
    void propertiesSetters() {
        properties.setEnabled(false);
        properties.setTimeoutSeconds(60);
        properties.setMaxLength(50);
        properties.setSnippetLength(200);

        assertFalse(properties.isEnabled());
        assertEquals(60, properties.getTimeoutSeconds());
        assertEquals(50, properties.getMaxLength());
        assertEquals(200, properties.getSnippetLength());
    }
}
