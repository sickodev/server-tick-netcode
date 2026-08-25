package com.netcode.gateway;

import com.netcode.gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller providing health check and operational metrics endpoints for liveness,
 * readiness probes, and telemetry monitoring.
 *
 * <p>Exposes:
 * <ul>
 *   <li>{@code GET /health} - Health status and operational metrics overview.</li>
 *   <li>{@code GET /actuator/metrics} and {@code GET /metrics} - Detailed gateway telemetry snapshot.</li>
 * </ul>
 * </p>
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final GatewayMetrics gatewayMetrics;

    /**
     * Constructs the HealthController with the operational metrics collector.
     *
     * @param gatewayMetrics The gateway telemetry metrics component.
     */
    public HealthController(GatewayMetrics gatewayMetrics) {
        this.gatewayMetrics = gatewayMetrics;
    }

    /**
     * Handles HTTP GET requests to {@code /health}.
     *
     * <p>Returns service status alongside real-time metrics including active WebSocket sessions,
     * total messages forwarded, dropped commands, and gRPC latency.</p>
     *
     * @return {@link ResponseEntity} containing health status and metric summary.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("[ws] Health check requested - service is UP");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.putAll(gatewayMetrics.getMetricsSnapshot());
        return ResponseEntity.ok(response);
    }

    /**
     * Handles HTTP GET requests to {@code /actuator/metrics} or {@code /metrics}.
     *
     * <p>Returns a complete telemetry snapshot of Gateway operations.</p>
     *
     * @return {@link ResponseEntity} containing all tracked metrics.
     */
    @GetMapping({"/actuator/metrics", "/metrics"})
    public ResponseEntity<Map<String, Object>> metrics() {
        return ResponseEntity.ok(gatewayMetrics.getMetricsSnapshot());
    }
}
