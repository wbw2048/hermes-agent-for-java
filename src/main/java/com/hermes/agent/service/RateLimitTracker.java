package com.hermes.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 速率限制追踪器。
 * <p>
 * 解析 LLM 响应中的 x-ratelimit-* headers，记录最近一次请求的用量状态。
 */
@Service
public class RateLimitTracker {

    private static final Logger log = LoggerFactory.getLogger(RateLimitTracker.class);

    /** 按 provider 分组的最新速率状态 */
    private final Map<String, RateLimitState> states = new ConcurrentHashMap<>();

    /**
     * 从 HTTP 响应 headers 中解析速率限制信息并更新状态。
     *
     * @param provider   提供商标识（如 "openai", "dashscope"）
     * @param headers    响应 headers（小写 key）
     */
    public void captureHeaders(String provider, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return;

        boolean hasAny = headers.keySet().stream().anyMatch(k -> k.startsWith("x-ratelimit-"));
        if (!hasAny) return;

        RateLimitState state = RateLimitState.fromHeaders(headers, provider);
        if (state != null) {
            states.put(provider, state);
            log.debug("Captured rate limit state for provider={}", provider);
        }
    }

    /**
     * 获取指定 provider 的最新速率状态。
     */
    public RateLimitState getState(String provider) {
        return states.get(provider);
    }

    /**
     * 获取所有 provider 的速率状态。
     */
    public Map<String, RateLimitState> getAllStates() {
        return Map.copyOf(states);
    }

    /**
     * 清除所有速率状态。
     */
    public void clear() {
        states.clear();
    }

    /**
     * 速率限制状态 Record。
     */
    public record RateLimitState(
            RateLimitBucket requestsMin,
            RateLimitBucket requestsHour,
            RateLimitBucket tokensMin,
            RateLimitBucket tokensHour,
            Instant capturedAt,
            String provider
    ) {
        public boolean hasData() {
            return !capturedAt.equals(Instant.EPOCH);
        }

        static RateLimitState fromHeaders(Map<String, String> headers, String provider) {
            Instant now = Instant.now();

            RateLimitBucket reqMin = bucket(headers, "requests", "");
            RateLimitBucket reqHour = bucket(headers, "requests", "-1h");
            RateLimitBucket tokMin = bucket(headers, "tokens", "");
            RateLimitBucket tokHour = bucket(headers, "tokens", "-1h");

            if (reqMin.limit() == 0 && reqHour.limit() == 0
                    && tokMin.limit() == 0 && tokHour.limit() == 0) {
                return null;
            }

            return new RateLimitState(reqMin, reqHour, tokMin, tokHour, now, provider);
        }

        private static RateLimitBucket bucket(Map<String, String> headers, String resource, String suffix) {
            String tag = resource + suffix;
            int limit = parseInt(headers.get("x-ratelimit-limit-" + tag));
            int remaining = parseInt(headers.get("x-ratelimit-remaining-" + tag));
            int resetSeconds = parseInt(headers.get("x-ratelimit-reset-" + tag));
            return new RateLimitBucket(limit, remaining, resetSeconds);
        }

        private static int parseInt(String value) {
            if (value == null) return 0;
            try {
                return (int) Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /**
     * 单个速率限制桶的快照。
     */
    public record RateLimitBucket(
            int limit,
            int remaining,
            int resetSeconds
    ) {
        public int used() {
            return Math.max(0, limit - remaining);
        }

        public double usagePct() {
            if (limit <= 0) return 0.0;
            return (double) used() / limit * 100.0;
        }
    }
}
