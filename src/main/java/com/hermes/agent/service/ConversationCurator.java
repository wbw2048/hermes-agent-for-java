package com.hermes.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 对话整理服务。
 * <p>
 * 去除无效轮次、合并短消息、去重工具调用，减少长对话的上下文消耗。
 */
@Service
public class ConversationCurator {

    private static final Logger log = LoggerFactory.getLogger(ConversationCurator.class);

    /** 短消息合并阈值（字符数） */
    private static final int SHORT_MSG_THRESHOLD = 20;

    /**
     * 整理指定会话的消息历史，返回整理结果。
     *
     * @param messages 原始消息列表
     * @return 整理后的消息列表
     */
    public CurateResult curate(List<Message> messages) {
        int beforeCount = messages.size();
        List<Message> result = new ArrayList<>();

        // 1. 移除空用户消息（保留 AssistantMessage 即使文本为空，可能含工具调用）
        List<Message> nonEmpty = messages.stream()
                .filter(m -> m.getMessageType() != MessageType.USER || (m.getText() != null && !m.getText().isBlank()))
                .toList();

        // 2. 合并连续短用户消息
        List<Message> merged = mergeShortUserMessages(nonEmpty);

        // 3. 去重连续相同工具调用
        List<Message> deduped = deduplicateToolCalls(merged);

        int afterCount = deduped.size();

        log.info("Curated conversation: {} -> {} messages (removed {})", beforeCount, afterCount, beforeCount - afterCount);
        return new CurateResult(beforeCount, afterCount, deduped);
    }

    private List<Message> mergeShortUserMessages(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean buffering = false;

        for (Message msg : messages) {
            if (msg.getMessageType() == MessageType.USER) {
                String text = msg.getText();
                if (text.length() <= SHORT_MSG_THRESHOLD) {
                    if (!buffering) {
                        buffer.append(text);
                        buffering = true;
                    } else {
                        buffer.append(" ").append(text);
                    }
                } else {
                    if (buffering) {
                        result.add(new UserMessage(buffer.toString()));
                        buffer.setLength(0);
                        buffering = false;
                    }
                    result.add(msg);
                }
            } else {
                if (buffering) {
                    result.add(new UserMessage(buffer.toString()));
                    buffer.setLength(0);
                    buffering = false;
                }
                result.add(msg);
            }
        }
        if (buffering) {
            result.add(new UserMessage(buffer.toString()));
        }
        return result;
    }

    private List<Message> deduplicateToolCalls(List<Message> messages) {
        List<Message> result = new ArrayList<>();
        Set<String> seenToolSignatures = new HashSet<>();

        for (Message msg : messages) {
            if (msg instanceof AssistantMessage am && !am.getToolCalls().isEmpty()) {
                String sig = am.getToolCalls().stream()
                        .map(tc -> tc.name() + ":" + tc.arguments())
                        .sorted()
                        .reduce((a, b) -> a + "|" + b)
                        .orElse("");
                if (seenToolSignatures.contains(sig)) {
                    log.debug("Skipping duplicate tool call: {}", sig);
                    continue;
                }
                seenToolSignatures.add(sig);
            }
            result.add(msg);
        }

        return result;
    }

    /**
     * 整理结果。
     */
    public record CurateResult(
            int messagesBefore,
            int messagesAfter,
            List<Message> curatedMessages
    ) {}
}
