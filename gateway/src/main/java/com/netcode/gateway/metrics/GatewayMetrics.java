package com.netcode.gateway.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Thread-safe operational metrics collector for the Spring Boot Gateway service.
 *
 * <p>Tracks real-time telemetry including:
 * <ul>
 *   <li>Active connected WebSocket sessions.</li>
 *   <li>Cumulative messages successfully forwarded to backend gRPC streams.</li>
 *   <li>Cumulative commands dropped due to rate limiting or invalid state.</li>
 *   <li>Exponentially weighted / average gRPC latency metrics.</li>
 * </ul>
 * </p>
 */
@Component
public class GatewayMetrics {

    private static final Logger log = LoggerFactory.getLogger(GatewayMetrics.class);

    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private final AtomicLong totalMessagesForwarded = new AtomicLong(0);
    private final AtomicLong totalDroppedCommands = new AtomicLong(0);
    private final AtomicLong totalSnapshotsReceived = new AtomicLong(0);

    private final AtomicLong latencyMeasurementCount = new AtomicLong(0);
    private final DoubleAdder totalGrpcLatencyMs = new DoubleAdder();
    private volatile double lastRecordedGrpcLatencyMs = 0.0;

    /**
     * Increments the count of active WebSocket sessions by one.
     *
     * @return New active session count.
     */
    public int incrementActiveSessions() {
        return activeSessions.incrementAndGet();
    }

    /**
     * Decrements the count of active WebSocket sessions by one (preventing negative values).
     *
     * @return New active session count.
     */
    public int decrementActiveSessions() {
        return activeSessions.updateAndGet(count -> Math.max(0, count - 1));
    }

    /**
     * Sets the active session count directly (e.g. synchronized from SessionManager).
     *
     * @param count Current active session count.
     */
    public void setActiveSessions(int count) {
        activeSessions.set(Math.max(0, count));
    }

    /**
     * Retrieves the current count of active WebSocket sessions.
     *
     * @return Active session count.
     */
    public int getActiveSessions() {
        return activeSessions.get();
    }

    /**
     * Records a message successfully forwarded to the Go game service over gRPC.
     *
     * @return Cumulative messages forwarded.
     */
    public long recordMessageForwarded() {
        return totalMessagesForwarded.incrementAndGet();
    }

    /**
     * Retrieves cumulative count of forwarded messages.
     *
     * @return Total forwarded message count.
     */
    public long getTotalMessagesForwarded() {
        return totalMessagesForwarded.get();
    }

    /**
     * Records a command dropped due to rate-limit violation or uninitialized stream.
     *
     * @return Cumulative dropped commands count.
     */
    public long recordDroppedCommand() {
        return totalDroppedCommands.incrementAndGet();
    }

    /**
     * Retrieves cumulative count of dropped commands.
     *
     * @return Total dropped command count.
     */
    public long getTotalDroppedCommands() {
        return totalDroppedCommands.get();
    }

    /**
     * Records a snapshot message received from the Go game service and pushed to a client.
     *
     * @return Cumulative snapshots received.
     */
    public long recordSnapshotReceived() {
        return totalSnapshotsReceived.incrementAndGet();
    }

    /**
     * Retrieves cumulative count of snapshots received.
     *
     * @return Total snapshots received count.
     */
    public long getTotalSnapshotsReceived() {
        return totalSnapshotsReceived.get();
    }

    /**
     * Records a measured gRPC round-trip or forwarding latency sample in milliseconds.
     *
     * @param latencyMs Duration in milliseconds.
     */
    public void recordGrpcLatency(double latencyMs) {
        if (latencyMs >= 0.0 && !Double.isNaN(latencyMs) && !Double.isInfinite(latencyMs)) {
            totalGrpcLatencyMs.add(latencyMs);
            latencyMeasurementCount.incrementAndGet();
            lastRecordedGrpcLatencyMs = latencyMs;
        }
    }

    /**
     * Calculates the average gRPC latency across all recorded samples.
     *
     * @return Average gRPC latency in milliseconds, or 0.0 if no measurements recorded.
     */
    public double getAverageGrpcLatencyMs() {
        long count = latencyMeasurementCount.get();
        if (count == 0) {
            return 0.0;
        }
        return totalGrpcLatencyMs.sum() / count;
    }

    /**
     * Retrieves the most recently recorded gRPC latency sample.
     *
     * @return Last latency measurement in milliseconds.
     */
    public double getLastRecordedGrpcLatencyMs() {
        return lastRecordedGrpcLatencyMs;
    }

    /**
     * Produces a structured snapshot map of all current metric counters and averages.
     *
     * @return Ordered map of metric names to values.
     */
    public Map<String, Object> getMetricsSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("activeSessions", getActiveSessions());
        snapshot.put("messagesForwarded", getTotalMessagesForwarded());
        snapshot.put("droppedCommands", getTotalDroppedCommands());
        snapshot.put("snapshotsReceived", getTotalSnapshotsReceived());
        snapshot.put("lastGrpcLatencyMs", Math.round(lastRecordedGrpcLatencyMs * 100.0) / 100.0);
        snapshot.put("avgGrpcLatencyMs", Math.round(getAverageGrpcLatencyMs() * 100.0) / 100.0);
        return snapshot;
    }

    /**
     * Resets all metric counters to zero (useful for testing).
     */
    public void reset() {
        activeSessions.set(0);
        totalMessagesForwarded.set(0);
        totalDroppedCommands.set(0);
        totalSnapshotsReceived.set(0);
        latencyMeasurementCount.set(0);
        totalGrpcLatencyMs.reset();
        lastRecordedGrpcLatencyMs = 0.0;
    }
}
