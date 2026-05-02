package com.hermes.agent.error;

import com.hermes.agent.error.ErrorClassifier.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器，为所有 REST 端点提供统一的错误响应。
 */
@RestControllerAdvice(basePackages = "com.hermes.agent.controller")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorClassifier errorClassifier;

    public GlobalExceptionHandler(ErrorClassifier errorClassifier) {
        this.errorClassifier = errorClassifier;
    }

    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<StandardErrorResponse> handleTransientAi(TransientAiException e) {
        ErrorType type = errorClassifier.classify(e);
        log.warn("Transient AI error: {} - {}", type, e.getMessage());
        StandardErrorResponse response = StandardErrorResponse.of(
                type.name(),
                errorClassifier.getUserMessage(type),
                e.getMessage(),
                extractSessionId(e));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<StandardErrorResponse> handleNonTransientAi(NonTransientAiException e) {
        ErrorType type = errorClassifier.classify(e);
        log.error("Non-transient AI error: {} - {}", type, e.getMessage());
        StandardErrorResponse response = StandardErrorResponse.of(
                type.name(),
                errorClassifier.getUserMessage(type),
                e.getMessage(),
                extractSessionId(e));
        HttpStatus status = (type == ErrorType.AUTH_FAILURE) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        StandardErrorResponse response = StandardErrorResponse.of(
                "INVALID_REQUEST", "请求参数无效", e.getMessage(), null);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<StandardErrorResponse> handleNotFound(NoResourceFoundException e) {
        StandardErrorResponse response = StandardErrorResponse.of(
                "NOT_FOUND", "资源不存在", e.getMessage(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardErrorResponse> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        StandardErrorResponse response = StandardErrorResponse.of(
                "INTERNAL_ERROR", "服务器内部错误", e.getMessage(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 从异常消息中提取 sessionId（用于日志关联）。
     */
    private String extractSessionId(Throwable e) {
        if (e.getMessage() != null && e.getMessage().contains("sessionId=")) {
            int start = e.getMessage().indexOf("sessionId=") + 10;
            int end = e.getMessage().indexOf(",", start);
            if (end < 0) end = e.getMessage().length();
            return e.getMessage().substring(start, end).trim();
        }
        return null;
    }
}
