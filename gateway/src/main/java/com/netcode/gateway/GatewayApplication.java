package com.netcode.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entrypoint for the Gateway WebSocket microservice.
 *
 * <p>The Gateway microservice bridges frontend clients (communicating via JSON over WebSocket)
 * with the authoritative Go game server (communicating via Protobuf over gRPC).
 * It manages client sessions, translates messages, and serves health check endpoints.</p>
 */
@SpringBootApplication
public class GatewayApplication {

    /**
     * Bootstraps and starts the Spring Boot Gateway microservice.
     *
     * @param args Command-line arguments passed during application startup.
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    /**
     * Provides a shared, thread-safe {@link ObjectMapper} bean for JSON serialization
     * and deserialization across the gateway components (WebSocket handlers, controllers, etc.).
     *
     * @return A configured {@link ObjectMapper} instance.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
