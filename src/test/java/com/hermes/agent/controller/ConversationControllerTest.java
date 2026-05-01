package com.hermes.agent.controller;

import com.hermes.agent.agent.SimpleAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link ConversationController} 的单元测试。
 * 覆盖四个 REST 端点的正常流程和异常场景。
 */
class ConversationControllerTest {

    private MockMvc mockMvc;
    private SimpleAgent agent;

    @BeforeEach
    void setUp() {
        agent = mock(SimpleAgent.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(agent)).build();
    }

    @Test
    void healthReturnsServiceInfo() throws Exception {
        when(agent.getAvailableTools()).thenReturn(List.of("echo", "getCurrentTime"));

        mockMvc.perform(get("/api/conversations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("hermes-agent"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.tools").value("echo, getCurrentTime"));
    }

    @Test
    void healthWithNoTools() throws Exception {
        when(agent.getAvailableTools()).thenReturn(List.of());

        mockMvc.perform(get("/api/conversations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.tools").value(""));
    }

    @Test
    void postMessageWithSessionId() throws Exception {
        when(agent.runConversation(eq("sess-1"), eq("hello"))).thenReturn("Hi there!");

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "hello", "sessionId": "sess-1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Hi there!"))
                .andExpect(jsonPath("$.sessionId").value("sess-1"));
    }

    @Test
    void postMessageWithoutSessionIdGeneratesUUID() throws Exception {
        when(agent.runConversation(anyString(), eq("hello"))).thenReturn("Hi!");

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "hello"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Hi!"))
                .andExpect(jsonPath("$.sessionId").isNotEmpty());
    }

    @Test
    void postMessageAgentReturnsError() throws Exception {
        when(agent.runConversation(anyString(), eq("hello"))).thenReturn("抱歉，处理您的请求时出现了错误: boom");

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message": "hello", "sessionId": "s1"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("抱歉，处理您的请求时出现了错误: boom"));
    }

    @Test
    void postMessageInvalidRequestBody() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHistoryReturnsMessages() throws Exception {
        List<Message> history = List.of(new UserMessage("hello"), new UserMessage("world"));
        when(agent.getConversationHistory("sess-1")).thenReturn(history);

        mockMvc.perform(get("/api/conversations/sess-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].messageType").value("USER"))
                .andExpect(jsonPath("$[1].text").value("world"));
    }

    @Test
    void getHistoryReturnsEmptyForUnknownSession() throws Exception {
        when(agent.getConversationHistory("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/conversations/unknown/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void deleteHistoryClearsSession() throws Exception {
        doNothing().when(agent).clearHistory("sess-1");

        mockMvc.perform(delete("/api/conversations/sess-1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("对话历史已清除"));

        verify(agent).clearHistory("sess-1");
    }
}
