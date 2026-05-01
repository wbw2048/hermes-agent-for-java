package com.hermes.agent.compressor;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 在压缩前快速剪枝旧的工具结果：去重、摘要、截断。
 */
@Component
public class ToolResultPruner {

    private static final int DUPLICATE_THRESHOLD_CHARS = 200;
    private static final int TOOL_ARGS_TRUNCATE = 500;

    /**
     * 对消息列表进行预压缩剪枝。
     *
     * @param messages       原始消息列表
     * @param protectTailCount 保护的尾部消息数量
     * @return 剪枝后的消息列表
     */
    public List<Message> prune(List<Message> messages, int protectTailCount) {
        if (messages.size() <= protectTailCount + 1) {
            return new ArrayList<>(messages);
        }

        int pruneBoundary = messages.size() - protectTailCount;

        // 建立 tool_call_id -> tool_name 的映射
        Map<String, String> callIdToTool = new HashMap<>();
        Map<String, String> callIdToArgs = new HashMap<>();
        for (Message msg : messages) {
            if (msg instanceof AssistantMessage assistant) {
                for (var tc : assistant.getToolCalls()) {
                    callIdToTool.put(tc.id(), tc.name());
                    callIdToArgs.put(tc.id(), tc.arguments());
                }
            }
        }

        List<Message> result = new ArrayList<>();
        Set<String> seenContent = new HashSet<>();

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);

            if (msg instanceof ToolResponseMessage toolResp && i < pruneBoundary) {
                var responses = toolResp.getResponses();
                if (!responses.isEmpty()) {
                    String content = responses.get(0).responseData();
                    if (content != null && content.length() > DUPLICATE_THRESHOLD_CHARS) {
                        String hash = Integer.toHexString(content.hashCode());
                        if (seenContent.contains(hash)) {
                            // 重复内容，替换为摘要
                            String callId = responses.get(0).id();
                            String toolName = callIdToTool.getOrDefault(callId, "unknown");
                            String summary = summarizeToolResult(toolName, callIdToArgs.get(callId), content);
                            List<ToolResponseMessage.ToolResponse> newResponses = new ArrayList<>();
                            for (var r : responses) {
                                newResponses.add(new ToolResponseMessage.ToolResponse(r.id(), r.name(), summary));
                            }
                            msg = ToolResponseMessage.builder().responses(newResponses).build();
                        } else {
                            seenContent.add(hash);
                        }
                    }
                }
            }

            // 截断过长 tool_call 参数（保持 AssistantMessage 完整性）
            if (msg instanceof AssistantMessage assistant && i < pruneBoundary) {
                msg = truncateToolCalls(assistant);
            }

            result.add(msg);
        }

        return result;
    }

    private AssistantMessage truncateToolCalls(AssistantMessage msg) {
        var toolCalls = msg.getToolCalls();
        boolean modified = false;
        List<AssistantMessage.ToolCall> newCalls = new ArrayList<>();
        for (var tc : toolCalls) {
            if (tc.arguments() != null && tc.arguments().length() > TOOL_ARGS_TRUNCATE) {
                String truncated = tc.arguments().substring(0, TOOL_ARGS_TRUNCATE) + "...[truncated]";
                newCalls.add(new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(), truncated));
                modified = true;
            } else {
                newCalls.add(tc);
            }
        }
        if (modified) {
            return AssistantMessage.builder()
                .content(msg.getText())
                .toolCalls(newCalls)
                .build();
        }
        return msg;
    }

    private String summarizeToolResult(String toolName, String toolArgs, String content) {
        int lineCount = content.split("\n", -1).length;
        int charCount = content.length();

        return switch (toolName) {
            case "executeCommand" -> String.format("[terminal] -> exit ?, %d lines output", lineCount);
            case "readFile" -> String.format("[read_file] (%d chars)", charCount);
            case "writeFile" -> String.format("[write_file] (%d lines)", lineCount);
            case "searchFiles" -> String.format("[search_files] %d chars result", charCount);
            default -> String.format("[%s] (%d chars result)", toolName, charCount);
        };
    }
}
