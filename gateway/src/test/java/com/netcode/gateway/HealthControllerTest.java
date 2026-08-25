package com.netcode.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for {@link HealthController}.
 *
 * <p>Verifies that the /health endpoint correctly responds with HTTP 200 OK
 * and the expected JSON payload format.</p>
 */
@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies that GET /health returns HTTP 200 OK with JSON body {"status":"ok"}.
     *
     * @throws Exception if an error occurs during mock request dispatch
     */
    @Test
    @DisplayName("GET /health should return 200 OK with status: ok")
    void healthEndpointReturnsOk() throws Exception {
        mockMvc.perform(get("/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
