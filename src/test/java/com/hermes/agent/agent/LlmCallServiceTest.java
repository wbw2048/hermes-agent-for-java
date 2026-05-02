package com.hermes.agent.agent;

import com.hermes.agent.config.ErrorHandlingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmCallServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    private LlmCallService service;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        doReturn(requestSpec).when(requestSpec).system(anyString());
        doReturn(requestSpec).when(requestSpec).messages(anyList());
        doReturn(requestSpec).when(requestSpec).tools(any(Object[].class));
        when(requestSpec.call()).thenReturn(callResponseSpec);

        ErrorHandlingProperties props = new ErrorHandlingProperties();
        props.setLlmMaxRetries(3);
        service = new LlmCallService(chatClientBuilder, props);
    }

    @Test
    void callLlmWithRetryReturnsResponseOnSuccess() {
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("Hello");
        when(assistantMessage.getMessageType()).thenReturn(
                org.springframework.ai.chat.messages.MessageType.ASSISTANT);
        when(assistantMessage.getToolCalls()).thenReturn(null);

        AssistantMessage result = service.callLlmWithRetry("system", List.of(new UserMessage("hi")), new Object[0]);

        assertEquals("Hello", result.getText());
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void callToolLoopWithRetryReturnsResponseOnSuccess() {
        when(callResponseSpec.chatResponse()).thenReturn(chatResponse);

        ChatResponse result = service.callToolLoopWithRetry("system", List.of(new UserMessage("hi")), new Object[0]);

        assertNotNull(result);
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void callLlmWithRetryThrowsAfterMaxRetries() {
        when(callResponseSpec.chatResponse())
                .thenThrow(new org.springframework.ai.retry.TransientAiException("timeout"));

        assertThrows(RuntimeException.class, () ->
                service.callLlmWithRetry("system", List.of(new UserMessage("hi")), new Object[0]));
    }

    @Test
    void callToolLoopWithRetryThrowsAfterMaxRetries() {
        when(callResponseSpec.chatResponse())
                .thenThrow(new org.springframework.ai.retry.TransientAiException("timeout"));

        assertThrows(RuntimeException.class, () ->
                service.callToolLoopWithRetry("system", List.of(new UserMessage("hi")), new Object[0]));
    }
}
