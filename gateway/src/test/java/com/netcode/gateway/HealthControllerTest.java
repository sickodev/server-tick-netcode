package com.netcode.gateway;

import com.netcode.gateway.metrics.GatewayMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link HealthController}.
 *
 * <p>Verifies that the /health and /actuator/metrics endpoints correctly respond with HTTP 200 OK
 * and expose the expected JSON status and telemetry payload format.</p>
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GatewayMetrics gatewayMetrics;

    /**
     * Verifies that GET /health returns HTTP 200 OK with JSON body containing status ok and metrics.
     *
     * @throws Exception if an error occurs during mock request dispatch
     */
    @Test
    @DisplayName("GET /health should return 200 OK with status: ok and telemetry fields")
    void healthEndpointReturnsOk() throws Exception {
        when(gatewayMetrics.getMetricsSnapshot()).thenReturn(Map.of(
                "activeSessions", 2,
                "messagesForwarded", 100L,
                "droppedCommands", 5L,
                "lastGrpcLatencyMs", 1.2
        ));

        mockMvc.perform(get("/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.activeSessions").value(2))
                .andExpect(jsonPath("$.messagesForwarded").value(100))
                .andExpect(jsonPath("$.droppedCommands").value(5));
    }

    /**
     * Verifies that GET /actuator/metrics returns HTTP 200 OK with the telemetry map.
     *
     * @throws Exception if an error occurs during mock request dispatch
     */
    @Test
    @DisplayName("GET /actuator/metrics should return 200 OK with metrics map")
    void actuatorMetricsEndpointReturnsMetrics() throws Exception {
        when(gatewayMetrics.getMetricsSnapshot()).thenReturn(Map.of(
                "activeSessions", 3,
                "messagesForwarded", 250L
        ));

        mockMvc.perform(get("/actuator/metrics").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.activeSessions").value(3))
                .andExpect(jsonPath("$.messagesForwarded").value(250));
    }
}
