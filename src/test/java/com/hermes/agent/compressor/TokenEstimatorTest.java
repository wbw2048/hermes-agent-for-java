package com.hermes.agent.compressor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TokenEstimator} 的测试。
 */
class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void estimatesShortMessage() {
        var msg = new UserMessage("hello");
        int tokens = estimator.estimate(msg);
        assertTrue(tokens > 0);
        // "hello" = 5 chars, 5/4 + 10 = ~11 tokens
        assertEquals(11, tokens);
    }

    @Test
    void estimatesLongMessage() {
        String content = "A".repeat(400);
        var msg = new UserMessage(content);
        int tokens = estimator.estimate(msg);
        // 400/4 + 10 = 110
        assertEquals(110, tokens);
    }

    @Test
    void estimatesAllMessages() {
        List<Message> msgs = List.of(
            new UserMessage("hello"),
            new UserMessage("world")
        );
        int total = estimator.estimateAll(msgs);
        // (5/4+10) + (5/4+10) = 11 + 11 = 22
        assertEquals(22, total);
    }

    @Test
    void estimatesChineseContent() {
        String content = "你好世界";
        var msg = new UserMessage(content);
        int tokens = estimator.estimate(msg);
        // 4 chars / 4 + 10 = 11
        assertEquals(11, tokens);
    }
}
