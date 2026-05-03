package com.hermes.agent.controller;

import com.hermes.agent.service.RateLimitTracker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * API 用量查询控制器。
 */
@RestController
@RequestMapping("/api/usage")
@CrossOrigin(origins = "*")
public class UsageController {

    private final RateLimitTracker rateLimitTracker;

    public UsageController(RateLimitTracker rateLimitTracker) {
        this.rateLimitTracker = rateLimitTracker;
    }

    /**
     * 获取速率限制状态。
     * GET /api/usage/rate-limits
     */
    @GetMapping("/rate-limits")
    public ResponseEntity<Map<String, Object>> getRateLimits() {
        var states = rateLimitTracker.getAllStates();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);

        Map<String, Object> providers = new HashMap<>();
        for (var entry : states.entrySet()) {
            var state = entry.getValue();
            if (!state.hasData()) continue;

            Map<String, Object> p = new HashMap<>();
            p.put("provider", state.provider());
            p.put("capturedAt", state.capturedAt().toString());
            p.put("requestsMin", toBucketMap(state.requestsMin()));
            p.put("requestsHour", toBucketMap(state.requestsHour()));
            p.put("tokensMin", toBucketMap(state.tokensMin()));
            p.put("tokensHour", toBucketMap(state.tokensHour()));
            providers.put(entry.getKey(), p);
        }
        result.put("providers", providers);
        return ResponseEntity.ok(result);
    }

    /**
     * 清除速率限制状态。
     * DELETE /api/usage/rate-limits
     */
    @DeleteMapping("/rate-limits")
    public ResponseEntity<Map<String, String>> clearRateLimits() {
        rateLimitTracker.clear();
        return ResponseEntity.ok(Map.of("status", "success", "message", "速率限制状态已清除"));
    }

    private Map<String, Object> toBucketMap(RateLimitTracker.RateLimitBucket bucket) {
        Map<String, Object> m = new HashMap<>();
        m.put("limit", bucket.limit());
        m.put("remaining", bucket.remaining());
        m.put("used", bucket.used());
        m.put("usagePct", Math.round(bucket.usagePct() * 10) / 10.0);
        m.put("resetSeconds", bucket.resetSeconds());
        return m;
    }
}
