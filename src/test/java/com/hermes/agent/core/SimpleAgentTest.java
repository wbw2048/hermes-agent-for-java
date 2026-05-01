package com.hermes.agent.core;

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
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SimpleAgentTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callSpec;

    @Mock
    private ChatResponse chatResponse;

    @Mock
    private Generation generation;

    @Mock
    private AssistantMessage assistantMessage;

    private SimpleAgent agent;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
    }

    private void createAgent(Object... tools) {
        agent = new SimpleAgent(
                chatClientBuilder,
                List.of(tools),
                "You are a helpful assistant."
        );
    }

    @Test
    void getAvailableToolsReturnsEmptyWhenNoToolsRegistered() {
        createAgent();
        assertTrue(agent.getAvailableTools().isEmpty());
    }

    @Test
    void getAvailableToolsReturnsToolNames() {
        createAgent(new SimpleToolBean());
        List<String> tools = agent.getAvailableTools();
        assertEquals(1, tools.size());
        assertTrue(tools.contains("testTool"));
    }

    @Test
    void clearHistoryRemovesSessionData() {
        createAgent();
        when(assistantMessage.getToolCalls()).thenReturn(List.of());
        when(assistantMessage.getText()).thenReturn("Hello!");

        agent.runConversation("session-1", "Hi");
        assertFalse(agent.getConversationHistory("session-1").isEmpty());

        agent.clearHistory("session-1");
        assertTrue(agent.getConversationHistory("session-1").isEmpty());
    }

    @Test
    void getConversationHistoryReturnsEmptyForUnknownSession() {
        createAgent();
        List<Message> history = agent.getConversationHistory("nonexistent");
        assertTrue(history.isEmpty());
    }

    @Test
    void runConversationReturnsResponse() {
        createAgent();
        when(assistantMessage.getText()).thenReturn("This is the AI response");

        String result = agent.runConversation("s1", "Hello");

        assertEquals("This is the AI response", result);
    }

    @Test
    void runConversationHandlesException() {
        createAgent();
        when(requestSpec.call()).thenThrow(new RuntimeException("LLM connection failed"));

        String result = agent.runConversation("s1", "Hello");

        assertTrue(result.startsWith("抱歉，处理您的请求时出现了错误"));
    }

    @Test
    void runConversationHistoryAccumulatesMessages() {
        createAgent();
        when(assistantMessage.getText()).thenReturn("Reply 1");

        agent.runConversation("s1", "Message 1");
        agent.runConversation("s1", "Message 2");

        List<Message> history = agent.getConversationHistory("s1");
        assertEquals(4, history.size());
    }

    // Helper bean with a @Tool annotated method for testing getAvailableTools
    static class SimpleToolBean {
        @org.springframework.ai.tool.annotation.Tool(description = "A test tool")
        public String testTool(String input) {
            return "{\"result\": \"" + input + "\"}";
        }
    }
}
