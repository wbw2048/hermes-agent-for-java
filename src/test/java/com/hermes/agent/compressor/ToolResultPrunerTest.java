package com.hermes.agent.compressor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolResultPruner} 的测试。
 */
class ToolResultPrunerTest {

    private final ToolResultPruner pruner = new ToolResultPruner();

    @Test
    void returnsOriginalForShortList() {
        List<Message> msgs = List.of(new UserMessage("hello"));
        var result = pruner.prune(msgs, 3);
        assertEquals(1, result.size());
    }

    @Test
    void deduplicatesIdenticalToolResults() {
        String longContent = "A".repeat(300);

        ToolResponseMessage toolResp1 = ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "readFile", longContent)))
            .build();

        ToolResponseMessage toolResp2 = ToolResponseMessage.builder()
            .responses(List.of(new ToolResponseMessage.ToolResponse("call-2", "readFile", longContent)))
            .build();

        List<Message> msgs = new ArrayList<>();
        msgs.add(new UserMessage("read file"));
        msgs.add(AssistantMessage.builder().content("ok").toolCalls(List.of(
            new AssistantMessage.ToolCall("call-1", "function", "readFile", "{}"),
            new AssistantMessage.ToolCall("call-2", "function", "readFile", "{}")
        )).build());
        msgs.add(toolResp1);
        msgs.add(toolResp2);

        var result = pruner.prune(msgs, 0);
        assertEquals(4, result.size());
        // Second tool result should be summarized (same content as first)
        var prunedResp2 = (ToolResponseMessage) result.get(3);
        String content = prunedResp2.getResponses().get(0).responseData();
        assertTrue(content.contains("[read_file]"));
    }

    @Test
    void truncatesLongToolCallArgs() {
        String longArgs = "B".repeat(600);
        AssistantMessage assistant = AssistantMessage.builder()
            .content("using tool")
            .toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "writeFile", longArgs)
            ))
            .build();

        List<Message> msgs = new ArrayList<>();
        msgs.add(new UserMessage("write file"));
        msgs.add(assistant);

        var result = pruner.prune(msgs, 0);
        var prunedAssistant = (AssistantMessage) result.get(1);
        String args = prunedAssistant.getToolCalls().get(0).arguments();
        assertTrue(args.length() < longArgs.length());
        assertTrue(args.contains("[truncated]"));
    }

    @Test
    void protectsTailMessages() {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            msgs.add(new UserMessage("msg" + i));
        }

        var result = pruner.prune(msgs, 3);
        assertEquals(6, result.size());
    }
}
