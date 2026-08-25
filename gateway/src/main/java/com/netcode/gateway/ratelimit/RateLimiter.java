package com.netcode.gateway.ratelimit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe Token-Bucket rate limiter enforcing per-session bandwidth restrictions on incoming user commands.
 *
 * <p>Protects the backend authoritative game service against command flooding, malicious spamming, and client
 * tick spikes by limiting each active WebSocket session to a calibrated token budget with burst allowance.</p>
 *
 * <p>Key characteristics:
 * <ul>
 *   <li>Maximum sustained rate: 128 commands/second (2x the authoritative 64Hz server tick rate).</li>
 *   <li>Burst headroom: 16 extra tokens for transient network/rendering frame bursts.</li>
 *   <li>Independent per-session state: flood from one client does not penalize other connected clients.</li>
 *   <li>Warning log throttling: warnings per offending session are throttled to at most once per second.</li>
 * </ul>
 * </p>
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    /**
     * Maximum sustained commands permitted per second (2x the 64Hz server tick rate).
     */
    public static final double MAX_CMDS_PER_SECOND = 128.0;

    /**
     * Transient burst headroom allowance beyond steady-state rate.
     */
    public static final double BURST_CAPACITY = 16.0;

    /**
     * Total maximum token capacity for a single session's token bucket.
     */
    public static final double TOTAL_CAPACITY = MAX_CMDS_PER_SECOND + BURST_CAPACITY;

    /**
     * Minimum interval in milliseconds between consecutive rate-limit warning logs for a given session.
     */
    public static final long WARN_THROTTLE_INTERVAL_MS = 1000L;

    /**
     * Map of active WebSocket session IDs to their dedicated token buckets.
     */
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    private final double maxPerSecond;
    private final double capacity;

    /**
     * Default constructor creating a standard 128 cmds/sec rate limiter with 16-token burst headroom.
     */
    public RateLimiter() {
        this(MAX_CMDS_PER_SECOND, TOTAL_CAPACITY);
    }

    /**
     * Parameterized constructor allowing custom sustained rate and total capacity configuration.
     *
     * @param maxPerSecond Maximum sustained tokens generated per second.
     * @param capacity Total token capacity including burst allowance.
     */
    public RateLimiter(double maxPerSecond, double capacity) {
        this.maxPerSecond = maxPerSecond;
        this.capacity = capacity;
    }

    /**
     * Attempts to acquire a single command processing token for the specified session ID.
     *
     * @param sessionId The unique WebSocket session identifier.
     * @return {@code true} if a token was acquired and the command should proceed; {@code false} if rate limit exceeded.
     */
    public boolean tryAcquire(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        TokenBucket bucket = buckets.computeIfAbsent(
                sessionId,
                id -> new TokenBucket(this.capacity, this.maxPerSecond)
        );
        return bucket.tryConsume(1.0);
    }

    /**
     * Checks whether a rate-limit warning should be logged for the given session ID,
     * enforcing a 1-second throttle interval to prevent log flooding.
     *
     * @param sessionId The unique WebSocket session identifier.
     * @return {@code true} if a warning log should be emitted; {@code false} if throttled.
     */
    public boolean shouldWarn(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        TokenBucket bucket = buckets.get(sessionId);
        if (bucket == null) {
            return true;
        }
        return bucket.shouldWarn(System.currentTimeMillis());
    }

    /**
     * Removes and cleans up the token bucket associated with a closed WebSocket session.
     *
     * @param sessionId The WebSocket session identifier to clean up.
     */
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            buckets.remove(sessionId);
            log.debug("[ratelimit] cleared rate limiter state for session {}", sessionId);
        }
    }

    /**
     * Returns the number of currently tracked sessions in the rate limiter.
     *
     * @return Count of active session buckets.
     */
    public int getTrackedSessionCount() {
        return buckets.size();
    }

    /**
     * Clears all session buckets (used for lifecycle management and testing).
     */
    public void clear() {
        buckets.clear();
    }

    /**
     * Graceful teardown hook called upon application shutdown.
     */
    @PreDestroy
    public void shutdown() {
        log.info("[ratelimit] shutting down rate limiter (clearing {} active session buckets)", buckets.size());
        buckets.clear();
    }

    /**
     * Internal stateful token bucket enforcing continuous refill based on elapsed wall-clock nanoseconds.
     */
    static class TokenBucket {
        private final double maxCapacity;
        private final double refillTokensPerNano;

        private double availableTokens;
        private long lastRefillNanos;
        private long lastWarnTimeMillis;

        /**
         * Initializes a token bucket starting with full capacity.
         *
         * @param capacity Total token storage capacity.
         * @param refillPerSecond Number of tokens refilled per second.
         */
        public TokenBucket(double capacity, double refillPerSecond) {
            this.maxCapacity = capacity;
            this.availableTokens = capacity;
            this.refillTokensPerNano = refillPerSecond / (double) TimeUnit.SECONDS.toNanos(1);
            this.lastRefillNanos = System.nanoTime();
            this.lastWarnTimeMillis = 0L;
        }

        /**
         * Atomically refills tokens based on elapsed nanoseconds and attempts consumption.
         *
         * @param tokensRequested Number of tokens to consume (typically 1.0).
         * @return {@code true} if sufficient tokens were available and deducted, {@code false} otherwise.
         */
        public synchronized boolean tryConsume(double tokensRequested) {
            long now = System.nanoTime();
            long elapsedNanos = now - lastRefillNanos;

            if (elapsedNanos > 0) {
                // Add newly generated tokens based on elapsed real-time
                double tokensToAdd = elapsedNanos * refillTokensPerNano;
                availableTokens = Math.min(maxCapacity, availableTokens + tokensToAdd);
                lastRefillNanos = now;
            }

            if (availableTokens >= tokensRequested) {
                availableTokens -= tokensRequested;
                return true;
            }

            return false;
        }

        /**
         * Throttles warning log triggers to at most once per {@link #WARN_THROTTLE_INTERVAL_MS}.
         *
         * @param nowMillis Current timestamp in epoch milliseconds.
         * @return {@code true} if warning is permitted, {@code false} if suppressed.
         */
        public synchronized boolean shouldWarn(long nowMillis) {
            if (nowMillis - lastWarnTimeMillis >= WARN_THROTTLE_INTERVAL_MS) {
                lastWarnTimeMillis = nowMillis;
                return true;
            }
            return false;
        }

        /**
         * Returns current available token level (for testing inspection).
         *
         * @return Available token count.
         */
        public synchronized double getAvailableTokens() {
            return availableTokens;
        }
    }
}
