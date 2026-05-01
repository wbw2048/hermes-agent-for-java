package com.hermes.agent.compressor;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 粗略估算消息列表的令牌数量。使用字符数/4 的近似值。
 */
@Component
public class TokenEstimator {

    private static final int CHARS_PER_TOKEN = 4;
    private static final int MESSAGE_OVERHEAD = 10;

    /**
     * 估算单条消息的令牌数。
     */
    public int estimate(Message message) {
        int chars = message.getText() != null ? message.getText().length() : 0;
        return chars / CHARS_PER_TOKEN + MESSAGE_OVERHEAD;
    }

    /**
     * 估算消息列表的总令牌数。
     */
    public int estimateAll(List<Message> messages) {
        return messages.stream().mapToInt(this::estimate).sum();
    }
}
