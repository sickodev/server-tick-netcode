package com.netcode.gateway.session;

import com.netcode.gateway.proto.ClientMessage;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link SessionManager}.
 */
class SessionManagerTest {

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
    }

    @Test
    @DisplayName("Should register, retrieve, and remove sessions correctly")
    void testSessionLifecycle() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        String sessionId = "sess-101";
        String playerId = "p-uuid-999";

        assertFalse(sessionManager.hasSession(sessionId));
        assertEquals(0, sessionManager.getActiveSessionCount());

        sessionManager.registerSession(sessionId, playerId, mockStream);

        assertTrue(sessionManager.hasSession(sessionId));
        assertEquals(playerId, sessionManager.getPlayerId(sessionId));
        assertEquals(mockStream, sessionManager.getStream(sessionId));
        assertEquals(1, sessionManager.getActiveSessionCount());
        assertNotNull(sessionManager.getAllPlayerIds().get(sessionId));

        sessionManager.removeSession(sessionId);

        assertFalse(sessionManager.hasSession(sessionId));
        assertNull(sessionManager.getPlayerId(sessionId));
        assertNull(sessionManager.getStream(sessionId));
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    @DisplayName("Should clear all sessions when clear() is invoked")
    void testClear() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        sessionManager.registerSession("sess-1", "p-1", mockStream);
        sessionManager.registerSession("sess-2", "p-2", mockStream);
        assertEquals(2, sessionManager.getActiveSessionCount());

        sessionManager.clear();
        assertEquals(0, sessionManager.getActiveSessionCount());
    }
}
