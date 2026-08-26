package com.netcode.gateway.grpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GoServiceClient}.
 */
class GoServiceClientTest {

    @Test
    @DisplayName("GoServiceClient should initialize channel and stub and shutdown cleanly")
    void testLifecycle() {
        // useTls=false: correct for local/internal plaintext gRPC connections
        GoServiceClient client = new GoServiceClient("localhost", 9090, false);
        assertEquals("localhost", client.getHost());
        assertEquals(9090, client.getPort());

        client.init();
        assertNotNull(client.getChannel(), "ManagedChannel should be initialized");
        assertNotNull(client.getStub(), "GameServiceStub should be initialized");

        client.shutdown();
        assertTrue(client.getChannel().isShutdown(), "ManagedChannel should be shut down");
    }
}
