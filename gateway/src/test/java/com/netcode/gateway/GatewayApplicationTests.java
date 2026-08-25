package com.netcode.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test verifying that the Spring Boot Gateway application context loads successfully.
 */
@SpringBootTest
class GatewayApplicationTests {

    /**
     * Verifies that all Spring beans and configuration context initialize without errors.
     */
    @Test
    @DisplayName("Application context loads cleanly")
    void contextLoads() {
        // Assert that Spring application context boots with no exceptions.
    }
}
