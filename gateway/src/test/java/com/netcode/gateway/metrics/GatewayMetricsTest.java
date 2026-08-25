package com.netcode.gateway.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link GatewayMetrics}.
 *
 * <p>Validates active session counting, dropped command counters, message forwarding tracking,
 * latency averaging, and metric snapshot generation.</p>
 */
class GatewayMetricsTest {

    private GatewayMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new GatewayMetrics();
    }

    @Test
    @DisplayName("active session tracking should increment and decrement cleanly without going negative")
    void trackActiveSessions() {
        assertEquals(0, metrics.getActiveSessions());

        metrics.incrementActiveSessions();
        metrics.incrementActiveSessions();
        assertEquals(2, metrics.getActiveSessions());

        metrics.decrementActiveSessions();
        assertEquals(1, metrics.getActiveSessions());

        metrics.decrementActiveSessions();
        metrics.decrementActiveSessions(); // Extra decrement
        assertEquals(0, metrics.getActiveSessions(), "Active sessions count should floor at zero");
    }

    @Test
    @DisplayName("message forwarding and dropped command counters should accumulate accurately")
    void accumulateMessageCounters() {
        metrics.recordMessageForwarded();
        metrics.recordMessageForwarded();
        metrics.recordDroppedCommand();
        metrics.recordSnapshotReceived();

        assertEquals(2, metrics.getTotalMessagesForwarded());
        assertEquals(1, metrics.getTotalDroppedCommands());
        assertEquals(1, metrics.getTotalSnapshotsReceived());
    }

    @Test
    @DisplayName("gRPC latency tracking should calculate average and last recorded sample accurately")
    void trackGrpcLatency() {
        assertEquals(0.0, metrics.getAverageGrpcLatencyMs(), 0.001);

        metrics.recordGrpcLatency(10.0);
        metrics.recordGrpcLatency(20.0);

        assertEquals(15.0, metrics.getAverageGrpcLatencyMs(), 0.001);
        assertEquals(20.0, metrics.getLastRecordedGrpcLatencyMs(), 0.001);
    }

    @Test
    @DisplayName("getMetricsSnapshot should return complete map containing all operational metrics")
    void metricsSnapshot() {
        metrics.incrementActiveSessions();
        metrics.recordMessageForwarded();
        metrics.recordDroppedCommand();
        metrics.recordGrpcLatency(5.5);

        Map<String, Object> snapshot = metrics.getMetricsSnapshot();
        assertNotNull(snapshot);
        assertEquals(1, snapshot.get("activeSessions"));
        assertEquals(1L, snapshot.get("messagesForwarded"));
        assertEquals(1L, snapshot.get("droppedCommands"));
        assertEquals(5.5, snapshot.get("lastGrpcLatencyMs"));
    }

    @Test
    @DisplayName("reset should restore all metrics to baseline zero state")
    void resetMetrics() {
        metrics.incrementActiveSessions();
        metrics.recordMessageForwarded();
        metrics.recordDroppedCommand();
        metrics.recordGrpcLatency(12.0);

        metrics.reset();
        assertEquals(0, metrics.getActiveSessions());
        assertEquals(0L, metrics.getTotalMessagesForwarded());
        assertEquals(0L, metrics.getTotalDroppedCommands());
        assertEquals(0.0, metrics.getAverageGrpcLatencyMs(), 0.001);
    }
}
