package com.netcode.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller providing health check endpoints for liveness and readiness probes.
 *
 * <p>Used by container orchestrators, load balancers, and monitoring agents to verify
 * that the Spring Boot Gateway service is up and accepting incoming connections.</p>
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    /**
     * Handles HTTP GET requests to {@code /health}.
     *
     * <p>Returns a simple JSON payload indicating that the Gateway process is running
     * and responsive.</p>
     *
     * @return {@link ResponseEntity} containing a key-value map {@code {"status": "ok"}} with HTTP 200 OK.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        log.info("[ws] Health check requested - service is UP");
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
