package com.hermes.agent.error;

import com.hermes.agent.error.ErrorClassifier.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorClassifierTest {

    private final ErrorClassifier classifier = new ErrorClassifier();

    @Test
    void classifyNullReturnsUnknown() {
        assertEquals(ErrorType.UNKNOWN, classifier.classify(null));
    }

    @Test
    void classifyTransientTimeout() {
        var ex = new TransientAiException("Request timeout");
        assertEquals(ErrorType.TIMEOUT, classifier.classify(ex));
    }

    @Test
    void classifyTransientRateLimit() {
        var ex = new TransientAiException("429 rate limit exceeded");
        assertEquals(ErrorType.RATE_LIMIT, classifier.classify(ex));
    }

    @Test
    void classifyTransientNetwork() {
        var ex = new TransientAiException("connection reset");
        assertEquals(ErrorType.NETWORK_ERROR, classifier.classify(ex));
    }

    @Test
    void classifyNonTransientAuth() {
        var ex = new NonTransientAiException("401 unauthorized");
        assertEquals(ErrorType.AUTH_FAILURE, classifier.classify(ex));
    }

    @Test
    void classifyNonTransientBadRequest() {
        var ex = new NonTransientAiException("invalid request format");
        assertEquals(ErrorType.UNKNOWN, classifier.classify(ex));
    }

    @Test
    void classifyNonTransientRateLimit() {
        var ex = new NonTransientAiException("429 throttled");
        assertEquals(ErrorType.RATE_LIMIT, classifier.classify(ex));
    }

    @Test
    void classifyToolError() {
        var ex = new RuntimeException("tool [tracker] file read failed");
        assertEquals(ErrorType.TOOL_ERROR, classifier.classify(ex));
    }

    @Test
    void classifyConnectionRefused() {
        var ex = new RuntimeException("connection refused");
        assertEquals(ErrorType.NETWORK_ERROR, classifier.classify(ex));
    }

    @Test
    void classifyUnknownException() {
        var ex = new RuntimeException("something went wrong");
        assertEquals(ErrorType.UNKNOWN, classifier.classify(ex));
    }

    @ParameterizedTest
    @EnumSource(ErrorType.class)
    void getUserMessageForAllTypes(ErrorType type) {
        String msg = classifier.getUserMessage(type);
        assertEquals(true, msg != null && !msg.isBlank());
    }

    @Test
    void classifyConnectException() {
        var ex = new ConnectException("Connection refused");
        assertEquals(ErrorType.NETWORK_ERROR, classifier.classify(ex));
    }
}
