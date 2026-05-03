package com.hermes.agent.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 速率限制追踪器测试。
 */
class RateLimitTrackerTest {

    private final RateLimitTracker tracker = new RateLimitTracker();

    @Test
    void capturesRateLimitHeaders() {
        Map<String, String> headers = Map.of(
                "x-ratelimit-limit-requests", "100",
                "x-ratelimit-remaining-requests", "95",
                "x-ratelimit-reset-requests", "30",
                "x-ratelimit-limit-tokens", "50000",
                "x-ratelimit-remaining-tokens", "48000",
                "x-ratelimit-reset-tokens", "45"
        );

        tracker.captureHeaders("test-provider", headers);

        var state = tracker.getState("test-provider");
        assertNotNull(state);
        assertTrue(state.hasData());
        assertEquals(100, state.requestsMin().limit());
        assertEquals(5, state.requestsMin().used());
        assertEquals("test-provider", state.provider());
    }

    @Test
    void ignoresMissingHeaders() {
        tracker.captureHeaders("no-data-provider", Map.of("content-type", "application/json"));
        assertNull(tracker.getState("no-data-provider"));
    }

    @Test
    void overwritesPreviousState() {
        Map<String, String> headers1 = Map.of(
                "x-ratelimit-limit-requests", "100",
                "x-ratelimit-remaining-requests", "50"
        );
        tracker.captureHeaders("p", headers1);

        Map<String, String> headers2 = Map.of(
                "x-ratelimit-limit-requests", "100",
                "x-ratelimit-remaining-requests", "10"
        );
        tracker.captureHeaders("p", headers2);

        var state = tracker.getState("p");
        assertNotNull(state);
        assertEquals(10, state.requestsMin().remaining());
    }

    @Test
    void clearRemovesAllStates() {
        Map<String, String> headers = Map.of(
                "x-ratelimit-limit-requests", "100",
                "x-ratelimit-remaining-requests", "99"
        );
        tracker.captureHeaders("p1", headers);
        tracker.captureHeaders("p2", headers);

        tracker.clear();

        assertTrue(tracker.getAllStates().isEmpty());
    }

    @Test
    void rateLimitBucketCalculations() {
        var bucket = new RateLimitTracker.RateLimitBucket(100, 80, 30);
        assertEquals(20, bucket.used());
        assertEquals(20.0, bucket.usagePct());
        assertEquals(100, bucket.limit());
        assertEquals(80, bucket.remaining());
        assertEquals(30, bucket.resetSeconds());
    }

    @Test
    void bucketWithZeroLimit() {
        var bucket = new RateLimitTracker.RateLimitBucket(0, 0, 0);
        assertEquals(0, bucket.used());
        assertEquals(0.0, bucket.usagePct());
    }

    @Test
    void parsesHourlyHeaders() {
        Map<String, String> headers = Map.of(
                "x-ratelimit-limit-requests-1h", "5000",
                "x-ratelimit-remaining-requests-1h", "4500",
                "x-ratelimit-reset-requests-1h", "3600",
                "x-ratelimit-limit-tokens-1h", "1000000",
                "x-ratelimit-remaining-tokens-1h", "900000",
                "x-ratelimit-reset-tokens-1h", "3600"
        );

        tracker.captureHeaders("hourly", headers);

        var state = tracker.getState("hourly");
        assertNotNull(state);
        assertEquals(5000, state.requestsHour().limit());
        assertEquals(500, state.requestsHour().used());
    }

    @Test
    void getAllStatesReturnsCopy() {
        Map<String, String> headers = Map.of(
                "x-ratelimit-limit-requests", "100",
                "x-ratelimit-remaining-requests", "99"
        );
        tracker.captureHeaders("p", headers);

        var states = tracker.getAllStates();
        assertTrue(states.containsKey("p"));
    }
}
