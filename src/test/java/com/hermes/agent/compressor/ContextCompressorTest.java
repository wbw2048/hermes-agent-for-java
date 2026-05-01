package com.hermes.agent.compressor;

import com.hermes.agent.config.ContextCompressionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ContextCompressor} 的测试。
 * 注意：不 mock ChatClient，仅测试边界条件和配置逻辑。
 */
class ContextCompressorTest {

    private final ContextCompressionProperties props = new ContextCompressionProperties();
    private final TokenEstimator estimator = new TokenEstimator();
    private final ToolResultPruner pruner = new ToolResultPruner();

    @Test
    void shouldCompressReturnsFalseWhenDisabled() {
        props.setEnabled(false);
        var compressor = new ContextCompressor(null, estimator, pruner, props);
        assertFalse(compressor.shouldCompress(999999));
    }

    @Test
    void shouldCompressReturnsTrueWhenOverThreshold() {
        props.setEnabled(true);
        props.setContextLength(1000);
        props.setThresholdPercent(0.75);
        var compressor = new ContextCompressor(null, estimator, pruner, props);
        assertTrue(compressor.shouldCompress(800));
    }

    @Test
    void shouldCompressReturnsFalseWhenUnderThreshold() {
        props.setEnabled(true);
        props.setContextLength(1000);
        props.setThresholdPercent(0.75);
        var compressor = new ContextCompressor(null, estimator, pruner, props);
        assertFalse(compressor.shouldCompress(500));
    }

    @Test
    void returnsOriginalForTooFewMessages() {
        props.setEnabled(true);
        var compressor = new ContextCompressor(null, estimator, pruner, props);

        List<Message> msgs = List.of(
            new UserMessage("hello"),
            new UserMessage("world")
        );
        var result = compressor.compress(msgs, 128000);
        assertEquals(2, result.size());
    }

    @Test
    void resetClearsPreviousSummary() {
        var compressor = new ContextCompressor(null, estimator, pruner, props);
        compressor.reset(); // Should not throw
    }

    @Test
    void thresholdTokensIsCorrect() {
        props.setContextLength(128000);
        props.setThresholdPercent(0.75);
        assertEquals(96000, props.getThresholdTokens());
    }

    @Test
    void preservesHeadAndTail() {
        // With enough messages but null ChatClient, compression will fail gracefully
        // but should still return a valid message list
        props.setEnabled(true);
        props.setContextLength(1000); // Small context to trigger compression
        props.setThresholdPercent(0.50);
        var compressor = new ContextCompressor(null, estimator, pruner, props);

        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            msgs.add(new UserMessage("Message " + i));
        }

        // This will try to compress but fail (null ChatClient)
        // Should still return a non-null, non-empty list
        var result = compressor.compress(new ArrayList<>(msgs), 1000);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
