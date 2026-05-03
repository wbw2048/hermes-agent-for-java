package com.hermes.agent.agent;

import com.hermes.agent.compressor.ContextCompressor;
import com.hermes.agent.compressor.TokenEstimator;
import com.hermes.agent.compressor.ToolResultPruner;
import com.hermes.agent.config.ContextCompressionProperties;
import com.hermes.agent.config.ErrorHandlingProperties;
import com.hermes.agent.config.MemoryProperties;
import com.hermes.agent.controller.ToolCallTracker;
import com.hermes.agent.error.ErrorClassifier;
import com.hermes.agent.memory.MemoryExtractor;
import com.hermes.agent.memory.MemoryManager;
import com.hermes.agent.memory.MemoryStore;
import com.hermes.agent.memory.MemoryThreatDetector;
import com.hermes.agent.prompt.PromptBuilder;
import com.hermes.agent.service.SessionStorageService;
import com.hermes.agent.tool.ToolSetManager;
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

import java.util.ArrayList;
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

    @Mock
    private ToolSetManager toolSetManager;

    @Mock
    private SessionStorageService sessionStorageService;

    @Mock
    private LlmCallService llmCallService;

    private SimpleAgent agent;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.messages(any(List.class))).thenReturn(requestSpec);
        when(requestSpec.tools(any(Object[].class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatResponse()).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(toolSetManager.getActiveToolSetNames()).thenReturn(List.of("test"));
        when(toolSetManager.getActiveToolBeans(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Default sessionStorageService behavior
        doNothing().when(sessionStorageService).createSession(anyString(), any());
        when(sessionStorageService.loadSession(anyString())).thenReturn(new ArrayList<>());
        doNothing().when(sessionStorageService).saveMessages(anyString(), anyList());
        doNothing().when(sessionStorageService).deleteSession(anyString());
        when(sessionStorageService.getSession(anyString())).thenReturn(null);
        when(sessionStorageService.listSessions()).thenReturn(List.of());

        // Default llmCallService behavior
        when(assistantMessage.getText()).thenReturn("OK");
        when(assistantMessage.getMessageType()).thenReturn(
                org.springframework.ai.chat.messages.MessageType.ASSISTANT);
        when(assistantMessage.getToolCalls()).thenReturn(null);
        when(llmCallService.callLlmWithRetry(anyString(), anyList(), any(Object[].class)))
                .thenReturn(assistantMessage);
        when(llmCallService.callToolLoopWithRetry(anyString(), anyList(), any(Object[].class)))
                .thenReturn(chatResponse);
    }

    private void createAgent(Object... tools) {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setEnabled(false);
        MemoryThreatDetector threatDetector = new MemoryThreatDetector();
        MemoryStore memStore = new MemoryStore(memProps, threatDetector);
        MemoryManager memManager = new MemoryManager(memProps);
        memManager.addProvider(new com.hermes.agent.memory.BuiltinMemoryProvider(memStore));
        MemoryExtractor memExtractor = mock(MemoryExtractor.class);

        PromptBuilder promptBuilder = new PromptBuilder("You are a helpful assistant.", memManager);
        ContextCompressionProperties compressionProps = new ContextCompressionProperties();
        compressionProps.setEnabled(false); // Disable compression in tests by default
        TokenEstimator estimator = new TokenEstimator();
        ToolResultPruner pruner = new ToolResultPruner();
        ContextCompressor compressor = new ContextCompressor(chatClientBuilder, estimator, pruner, compressionProps);
        ErrorHandlingProperties errorProps = new ErrorHandlingProperties();
        ToolCallTracker toolCallTracker = new ToolCallTracker(errorProps);

        agent = new SimpleAgent(
                chatClientBuilder,
                toolSetManager,
                List.of(tools),
                sessionStorageService,
                promptBuilder,
                compressor,
                estimator,
                compressionProps,
                toolCallTracker,
                llmCallService,
                new ErrorClassifier(),
                memManager,
                memExtractor,
                memProps,
                mock(com.hermes.agent.service.TitleGeneratorService.class),
                mock(com.hermes.agent.config.TitleGenerationProperties.class),
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

        // Simulate history after first conversation
        when(sessionStorageService.loadSession("session-1"))
                .thenReturn(new ArrayList<>())
                .thenReturn(List.of(new UserMessage("Hi"), assistantMessage));

        agent.runConversation("session-1", "Hi");
        assertFalse(agent.getConversationHistory("session-1").isEmpty());

        agent.clearHistory("session-1");
        verify(sessionStorageService).deleteSession("session-1");
    }

    @Test
    void getConversationHistoryReturnsEmptyForUnknownSession() {
        createAgent();
        when(sessionStorageService.loadSession("nonexistent")).thenReturn(new ArrayList<>());
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
        when(llmCallService.callLlmWithRetry(anyString(), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("LLM connection failed"));

        String result = agent.runConversation("s1", "Hello");

        assertTrue(result.startsWith("抱歉，处理您的请求时出现错误"));
    }

    @Test
    void runConversationHistoryAccumulatesMessages() {
        createAgent();
        when(assistantMessage.getText()).thenReturn("Reply 1");
        when(assistantMessage.getToolCalls()).thenReturn(List.of());

        // First call: empty history
        when(sessionStorageService.loadSession("s1"))
                .thenReturn(new ArrayList<>())
                // Second call: 2 messages from first round
                .thenReturn(List.of(new UserMessage("Message 1"), assistantMessage));

        agent.runConversation("s1", "Message 1");
        agent.runConversation("s1", "Message 2");

        verify(sessionStorageService, times(2)).saveMessages(eq("s1"), anyList());
    }

    @Test
    void runConversationCreatesSession() {
        createAgent();
        when(assistantMessage.getText()).thenReturn("Hi");

        agent.runConversation("new-session", "Hello");

        verify(sessionStorageService).createSession("new-session", null);
    }

    // Helper bean with a @Tool annotated method for testing getAvailableTools
    static class SimpleToolBean {
        @org.springframework.ai.tool.annotation.Tool(description = "A test tool")
        public String testTool(String input) {
            return "{\"result\": \"" + input + "\"}";
        }
    }
}
