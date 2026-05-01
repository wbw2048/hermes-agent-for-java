package com.hermes.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.agent.entity.MessageEntity;
import com.hermes.agent.entity.SessionEntity;
import com.hermes.agent.repository.MessageRepository;
import com.hermes.agent.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionStorageServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private MessageRepository messageRepository;

    private SessionStorageService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SessionStorageService(sessionRepository, messageRepository, objectMapper);
    }

    @Test
    void createSessionCreatesNewSession() {
        when(sessionRepository.existsById("s1")).thenReturn(false);

        service.createSession("s1", "Test Session");

        ArgumentCaptor<SessionEntity> captor = ArgumentCaptor.forClass(SessionEntity.class);
        verify(sessionRepository).save(captor.capture());

        SessionEntity saved = captor.getValue();
        assertEquals("s1", saved.getId());
        assertEquals("Test Session", saved.getTitle());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void createSessionSkipsExistingSession() {
        when(sessionRepository.existsById("s1")).thenReturn(true);

        service.createSession("s1", "Test");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void saveMessagesPersistsMessagesAndUpdatesSession() {
        String sessionId = "s1";
        when(messageRepository.countBySessionId(sessionId)).thenReturn(0L, 2L);
        when(sessionRepository.findById(sessionId)).thenReturn(
                Optional.of(new SessionEntity(sessionId, "Test", Instant.now(), Instant.now()))
        );

        List<Message> messages = List.of(
                new UserMessage("Hello"),
                AssistantMessage.builder().content("Hi there!").build()
        );

        service.saveMessages(sessionId, messages);

        ArgumentCaptor<List<MessageEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(messageRepository).saveAll(captor.capture());

        List<MessageEntity> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals("USER", saved.get(0).getRole());
        assertEquals("ASSISTANT", saved.get(1).getRole());
        assertEquals(0, saved.get(0).getOrderIndex());
        assertEquals(1, saved.get(1).getOrderIndex());
    }

    @Test
    void loadSessionReturnsMessagesInOrder() {
        String sessionId = "s1";
        Instant now = Instant.now();
        List<MessageEntity> entities = List.of(
                new MessageEntity(sessionId, "USER", "Hello", null, null, now, 0),
                new MessageEntity(sessionId, "ASSISTANT", "Hi!", null, null, now, 1)
        );
        when(messageRepository.findBySessionIdOrderByOrderIndexAsc(sessionId)).thenReturn(entities);

        List<Message> messages = service.loadSession(sessionId);

        assertEquals(2, messages.size());
        assertEquals("Hello", messages.get(0).getText());
        assertEquals("Hi!", messages.get(1).getText());
    }

    @Test
    void loadSessionReturnsEmptyForNoMessages() {
        when(messageRepository.findBySessionIdOrderByOrderIndexAsc("empty")).thenReturn(List.of());

        List<Message> messages = service.loadSession("empty");

        assertTrue(messages.isEmpty());
    }

    @Test
    void deleteSessionRemovesMessagesAndSession() {
        service.deleteSession("s1");

        verify(messageRepository).deleteBySessionId("s1");
        verify(sessionRepository).deleteById("s1");
    }

    @Test
    void updateSessionTitleUpdatesTitleAndTimestamp() {
        Instant before = Instant.now();
        SessionEntity session = new SessionEntity("s1", "Old", before, before);
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));

        service.updateSessionTitle("s1", "New Title");

        assertEquals("New Title", session.getTitle());
        assertTrue(session.getUpdatedAt().isAfter(before) || session.getUpdatedAt().equals(before));
        verify(sessionRepository).save(session);
    }

    @Test
    void listSessionsReturnsSortedByUpdatedAtDesc() {
        List<SessionEntity> sessions = List.of(
                new SessionEntity("s1", "Recent", Instant.now(), Instant.now()),
                new SessionEntity("s2", "Old", Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3600))
        );
        when(sessionRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(sessions);

        List<SessionEntity> result = service.listSessions();

        assertEquals(2, result.size());
        assertEquals("s1", result.get(0).getId());
    }

    @Test
    void getSessionReturnsSessionIfExists() {
        SessionEntity entity = new SessionEntity("s1", "Test", Instant.now(), Instant.now());
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(entity));

        SessionEntity result = service.getSession("s1");

        assertNotNull(result);
        assertEquals("s1", result.getId());
    }

    @Test
    void getSessionReturnsNullIfNotExists() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());

        SessionEntity result = service.getSession("missing");

        assertNull(result);
    }
}
