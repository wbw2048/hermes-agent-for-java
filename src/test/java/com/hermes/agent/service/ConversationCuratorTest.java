package com.hermes.agent.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对话整理服务测试。
 */
class ConversationCuratorTest {

    private final ConversationCurator curator = new ConversationCurator();

    @Test
    void removesEmptyMessages() {
        List<Message> messages = List.of(
                new UserMessage("Hello"),
                new UserMessage("   "),
                new UserMessage("World")
        );

        ConversationCurator.CurateResult result = curator.curate(messages);
        // Empty message removed, remaining short messages merged into one
        assertEquals(1, result.messagesAfter());
        assertTrue(result.messagesBefore() > result.messagesAfter());
    }

    @Test
    void mergesShortUserMessages() {
        // Short consecutive user messages (< 20 chars) should be merged
        List<Message> messages = List.of(
                new UserMessage("Hi"),
                new UserMessage("Yes"),
                new UserMessage("OK")
        );

        ConversationCurator.CurateResult result = curator.curate(messages);
        // All short messages merged into one
        assertEquals(1, result.messagesAfter());
    }

    @Test
    void doesNotMergeLongMessages() {
        List<Message> messages = List.of(
                new UserMessage("This is a longer message that exceeds the threshold of twenty characters"),
                new UserMessage("Another long message here also exceeding threshold")
        );

        ConversationCurator.CurateResult result = curator.curate(messages);
        assertEquals(2, result.messagesAfter());
    }

    @Test
    void removesDuplicateToolCalls() {
        AssistantMessage toolCall1 = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("tc1", "function", "readFile", "{\"path\":\"a.txt\"}")
                ))
                .build();
        AssistantMessage toolCall2 = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("tc2", "function", "readFile", "{\"path\":\"a.txt\"}")
                ))
                .build();

        List<Message> messages = List.of(toolCall1, toolCall2);

        ConversationCurator.CurateResult result = curator.curate(messages);
        // Duplicate tool call removed
        assertEquals(1, result.messagesAfter());
    }

    @Test
    void preservesUniqueToolCalls() {
        AssistantMessage toolCall1 = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("tc1", "function", "readFile", "{\"path\":\"a.txt\"}")
                ))
                .build();
        AssistantMessage toolCall2 = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("tc2", "function", "writeFile", "{\"path\":\"b.txt\"}")
                ))
                .build();

        List<Message> messages = List.of(toolCall1, toolCall2);

        ConversationCurator.CurateResult result = curator.curate(messages);
        // Both unique tool calls preserved
        assertEquals(2, result.messagesAfter());
    }

    @Test
    void mixedConversation() {
        AssistantMessage toolCall = AssistantMessage.builder()
                .content("Result")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("tc1", "function", "readFile", "{\"path\":\"a.txt\"}")
                ))
                .build();

        List<Message> messages = List.of(
                new UserMessage("Hi"),
                new UserMessage("Yo"),
                toolCall,
                new UserMessage("This is a much longer message that should not be merged")
        );

        ConversationCurator.CurateResult result = curator.curate(messages);
        // Short messages merged, tool call and long message preserved
        assertTrue(result.messagesAfter() < result.messagesBefore());
    }

    @Test
    void emptyListReturnsEmpty() {
        ConversationCurator.CurateResult result = curator.curate(List.of());
        assertEquals(0, result.messagesAfter());
        assertEquals(0, result.messagesBefore());
    }
}
