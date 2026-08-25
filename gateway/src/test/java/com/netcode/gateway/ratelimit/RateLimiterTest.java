package com.netcode.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RateLimiter}.
 *
 * <p>Validates burst tolerance, sustained throughput limits, independent session isolation,
 * warning log throttling, and cleanup upon session termination.</p>
 */
class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Small capacity test limiter: sustained 10/sec, burst capacity 5 (total 15)
        rateLimiter = new RateLimiter(10.0, 15.0);
    }

    @Test
    @DisplayName("tryAcquire should allow burst up to total capacity")
    void allowBurstUpToCapacity() {
        String session = "sess-burst-1";
        for (int i = 0; i < 15; i++) {
            assertTrue(rateLimiter.tryAcquire(session), "Token " + i + " should be granted within capacity");
        }
        // 16th immediate attempt exceeds capacity
        assertFalse(rateLimiter.tryAcquire(session), "Token 16 should be denied when capacity exhausted");
    }

    @Test
    @DisplayName("tryAcquire should handle null or blank session IDs gracefully")
    void handleInvalidSessionIds() {
        assertFalse(rateLimiter.tryAcquire(null));
        assertFalse(rateLimiter.tryAcquire(""));
        assertFalse(rateLimiter.tryAcquire("   "));
    }

    @Test
    @DisplayName("rate limiter state should be isolated per session")
    void sessionIsolation() {
        String sessionA = "sess-A";
        String sessionB = "sess-B";

        // Exhaust session A's capacity
        for (int i = 0; i < 15; i++) {
            assertTrue(rateLimiter.tryAcquire(sessionA));
        }
        assertFalse(rateLimiter.tryAcquire(sessionA));

        // Session B should still have full capacity
        assertTrue(rateLimiter.tryAcquire(sessionB));
        assertEquals(2, rateLimiter.getTrackedSessionCount());
    }

    @Test
    @DisplayName("removeSession should evict session bucket without leaks")
    void removeSessionCleansState() {
        String session = "sess-temp";
        rateLimiter.tryAcquire(session);
        assertEquals(1, rateLimiter.getTrackedSessionCount());

        rateLimiter.removeSession(session);
        assertEquals(0, rateLimiter.getTrackedSessionCount());
    }

    @Test
    @DisplayName("shouldWarn should throttle warnings to at most once per throttle interval")
    void warningThrottling() {
        String session = "sess-warn-test";
        rateLimiter.tryAcquire(session);

        // First warning is permitted
        assertTrue(rateLimiter.shouldWarn(session));
        // Immediate subsequent warning is throttled
        assertFalse(rateLimiter.shouldWarn(session));
    }

    @Test
    @DisplayName("clear and shutdown should reset all internal buckets")
    void clearAndShutdown() {
        rateLimiter.tryAcquire("s1");
        rateLimiter.tryAcquire("s2");
        assertEquals(2, rateLimiter.getTrackedSessionCount());

        rateLimiter.clear();
        assertEquals(0, rateLimiter.getTrackedSessionCount());

        rateLimiter.tryAcquire("s3");
        rateLimiter.shutdown();
        assertEquals(0, rateLimiter.getTrackedSessionCount());
    }
}
