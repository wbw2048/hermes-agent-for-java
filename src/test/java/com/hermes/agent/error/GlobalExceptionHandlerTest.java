package com.hermes.agent.error;

import com.hermes.agent.error.ErrorClassifier.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ErrorClassifier());
    }

    @Test
    void handleTransientAiReturns503() {
        var ex = new TransientAiException("network error");
        ResponseEntity<StandardErrorResponse> response = handler.handleTransientAi(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NETWORK_ERROR", response.getBody().errorCode());
    }

    @Test
    void handleTransientAiTimeoutReturnsCorrectCode() {
        var ex = new TransientAiException("request timeout");
        ResponseEntity<StandardErrorResponse> response = handler.handleTransientAi(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("TIMEOUT", response.getBody().errorCode());
    }

    @Test
    void handleNonTransientAiReturns400() {
        var ex = new NonTransientAiException("invalid input");
        ResponseEntity<StandardErrorResponse> response = handler.handleNonTransientAi(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("UNKNOWN", response.getBody().errorCode());
    }

    @Test
    void handleAuthFailureReturns401() {
        var ex = new NonTransientAiException("401 unauthorized");
        ResponseEntity<StandardErrorResponse> response = handler.handleNonTransientAi(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("AUTH_FAILURE", response.getBody().errorCode());
    }

    @Test
    void handleIllegalArgumentReturns400() {
        var ex = new IllegalArgumentException("bad argument");
        ResponseEntity<StandardErrorResponse> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().errorCode());
    }

    @Test
    void handleGenericExceptionReturns500() {
        var ex = new RuntimeException("unexpected error");
        ResponseEntity<StandardErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().errorCode());
    }

    @Test
    void handleNoResourceFoundReturns404() {
        var ex = new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/missing");
        ResponseEntity<StandardErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("NOT_FOUND", response.getBody().errorCode());
    }

    @Test
    void errorResponseHasTimestamp() {
        var ex = new TransientAiException("test");
        var response = handler.handleTransientAi(ex).getBody();
        assertTrue(response.timestamp() > 0);
    }

    @Test
    void errorResponseHasErrorCode() {
        var ex = new TransientAiException("test");
        var response = handler.handleTransientAi(ex).getBody();
        assertNotNull(response.errorCode());
    }
}
