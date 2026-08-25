package com.netcode.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcode.gateway.grpc.GoServiceClient;
import com.netcode.gateway.metrics.GatewayMetrics;
import com.netcode.gateway.proto.BulletState;
import com.netcode.gateway.proto.ClientMessage;
import com.netcode.gateway.proto.EntityState;
import com.netcode.gateway.proto.JoinResponse;
import com.netcode.gateway.proto.ServerMessage;
import com.netcode.gateway.proto.Snapshot;
import com.netcode.gateway.ratelimit.RateLimiter;
import com.netcode.gateway.session.SessionManager;
import com.netcode.gateway.validation.InputValidator;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for {@link GameWebSocketHandler}.
 *
 * <p>Validates WebSocket lifecycle, join negotiation with name sanitization, rate limiting,
 * coordinate clamping, bidirectional gRPC streaming, snapshot delivery, metrics tracking, and disconnect teardown.</p>
 */
class GameWebSocketHandlerTest {

    private ObjectMapper objectMapper;
    private GoServiceClient mockGoServiceClient;
    private SessionManager sessionManager;
    private RateLimiter rateLimiter;
    private InputValidator inputValidator;
    private GatewayMetrics gatewayMetrics;
    private GameWebSocketHandler handler;
    private WebSocketSession mockSession;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockGoServiceClient = mock(GoServiceClient.class);
        sessionManager = new SessionManager();
        rateLimiter = new RateLimiter();
        inputValidator = new InputValidator();
        gatewayMetrics = new GatewayMetrics();

        handler = new GameWebSocketHandler(
                objectMapper,
                mockGoServiceClient,
                sessionManager,
                rateLimiter,
                inputValidator,
                gatewayMetrics
        );

        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn("sess-test-123");
        when(mockSession.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("afterConnectionEstablished should increment active sessions metric")
    void handleConnectionEstablished() {
        assertEquals(0, gatewayMetrics.getActiveSessions());
        assertDoesNotThrow(() -> handler.afterConnectionEstablished(mockSession));
        assertEquals(1, gatewayMetrics.getActiveSessions());
    }

    @Test
    @DisplayName("afterConnectionClosed should clean up session, decrement active sessions, and send LeaveRequest")
    void handleConnectionClosedWithActiveStream() {
        gatewayMetrics.incrementActiveSessions();
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        sessionManager.registerSession("sess-test-123", "player-abc", mockStream);
        rateLimiter.tryAcquire("sess-test-123");

        handler.afterConnectionClosed(mockSession, CloseStatus.NORMAL);

        ArgumentCaptor<ClientMessage> captor = ArgumentCaptor.forClass(ClientMessage.class);
        verify(mockStream).onNext(captor.capture());
        verify(mockStream).onCompleted();

        ClientMessage sent = captor.getValue();
        assertTrue(sent.hasLeaveRequest());
        assertEquals("player-abc", sent.getLeaveRequest().getPlayerId());
        assertFalse(sessionManager.hasSession("sess-test-123"));
        assertEquals(0, gatewayMetrics.getActiveSessions());
        assertEquals(0, rateLimiter.getTrackedSessionCount());
    }

    @Test
    @DisplayName("handleTextMessage with join should sanitize name, open gRPC stream, and send JoinRequest")
    void handleValidJoinMessage() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        when(mockGoServiceClient.play(any())).thenReturn(mockStream);

        String joinJson = """
                {
                    "type": "join",
                    "name": "<b>AcePilot</b>"
                }
                """;

        handler.handleTextMessage(mockSession, new TextMessage(joinJson));

        verify(mockGoServiceClient, times(1)).play(any());
        assertTrue(sessionManager.hasSession("sess-test-123"));
        String playerId = sessionManager.getPlayerId("sess-test-123");
        assertNotNull(playerId);

        ArgumentCaptor<ClientMessage> captor = ArgumentCaptor.forClass(ClientMessage.class);
        verify(mockStream, times(1)).onNext(captor.capture());

        ClientMessage sent = captor.getValue();
        assertTrue(sent.hasJoinRequest());
        // Name should have HTML stripped
        assertEquals("AcePilot", sent.getJoinRequest().getName());
        assertEquals(playerId, sent.getJoinRequest().getPlayerId());
        assertEquals(1, gatewayMetrics.getTotalMessagesForwarded());
    }

    @Test
    @DisplayName("handleTextMessage with duplicate join should ignore second join request")
    void handleDuplicateJoinMessage() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        when(mockGoServiceClient.play(any())).thenReturn(mockStream);

        String joinJson = """
                {
                    "type": "join",
                    "name": "AcePilot"
                }
                """;

        handler.handleTextMessage(mockSession, new TextMessage(joinJson));
        handler.handleTextMessage(mockSession, new TextMessage(joinJson));

        // Should only open gRPC stream once
        verify(mockGoServiceClient, times(1)).play(any());
        verify(mockStream, times(1)).onNext(any());
    }

    @Test
    @DisplayName("handleTextMessage with user_cmd should clamp inputs and forward to gRPC stream")
    void handleValidUserCmdMessage() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        sessionManager.registerSession("sess-test-123", "verified-player-1", mockStream);

        String cmdJson = """
                {
                    "type": "user_cmd",
                    "seq": 105,
                    "timestamp": 1724601234567,
                    "dx": 2.5,
                    "dy": -3.0,
                    "aim_angle": 3.14159,
                    "fire": true
                }
                """;

        handler.handleTextMessage(mockSession, new TextMessage(cmdJson));

        ArgumentCaptor<ClientMessage> captor = ArgumentCaptor.forClass(ClientMessage.class);
        verify(mockStream, times(1)).onNext(captor.capture());

        ClientMessage sent = captor.getValue();
        assertTrue(sent.hasUserCmd());
        assertEquals("verified-player-1", sent.getUserCmd().getPlayerId());
        assertEquals(105, sent.getUserCmd().getSeq());
        assertEquals(1724601234567L, sent.getUserCmd().getTimestamp());
        // dx and dy clamped to [-1.0, 1.0]
        assertEquals(1.0f, sent.getUserCmd().getDx(), 0.001f);
        assertEquals(-1.0f, sent.getUserCmd().getDy(), 0.001f);
        assertEquals(3.14159f, sent.getUserCmd().getAimAngle(), 0.001f);
        assertTrue(sent.getUserCmd().getFire());
        assertEquals(1, gatewayMetrics.getTotalMessagesForwarded());
    }

    @Test
    @DisplayName("handleTextMessage exceeding rate limit should drop command and increment dropped metric")
    void handleRateLimitExceeded() {
        // Use a restrictive rate limiter for test: 2 cmds/sec, capacity 2
        RateLimiter tightLimiter = new RateLimiter(2.0, 2.0);
        GameWebSocketHandler rateLimitedHandler = new GameWebSocketHandler(
                objectMapper,
                mockGoServiceClient,
                sessionManager,
                tightLimiter,
                inputValidator,
                gatewayMetrics
        );

        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        sessionManager.registerSession("sess-test-123", "p-test", mockStream);

        String cmdJson = """
                {
                    "type": "user_cmd",
                    "seq": 1,
                    "dx": 0.0,
                    "dy": 0.0,
                    "aim_angle": 0.0,
                    "fire": false
                }
                """;

        // Consume all 2 tokens
        rateLimitedHandler.handleTextMessage(mockSession, new TextMessage(cmdJson));
        rateLimitedHandler.handleTextMessage(mockSession, new TextMessage(cmdJson));
        assertEquals(2, gatewayMetrics.getTotalMessagesForwarded());
        assertEquals(0, gatewayMetrics.getTotalDroppedCommands());

        // 3rd command should be dropped by rate limiter
        rateLimitedHandler.handleTextMessage(mockSession, new TextMessage(cmdJson));
        assertEquals(2, gatewayMetrics.getTotalMessagesForwarded());
        assertEquals(1, gatewayMetrics.getTotalDroppedCommands());
        verify(mockStream, times(2)).onNext(any());
    }

    @Test
    @DisplayName("handleTextMessage with user_cmd before join should be dropped gracefully and increment dropped metric")
    void handleUserCmdBeforeJoin() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);

        String cmdJson = """
                {
                    "type": "user_cmd",
                    "seq": 105,
                    "dx": 1.0,
                    "dy": -1.0,
                    "aimAngle": 1.57,
                    "fire": false
                }
                """;

        handler.handleTextMessage(mockSession, new TextMessage(cmdJson));

        verify(mockStream, never()).onNext(any());
        assertEquals(1, gatewayMetrics.getTotalDroppedCommands());
    }

    @Test
    @DisplayName("handleServerMessage should serialize Snapshot to JSON and send to WebSocket session")
    void handleServerMessageSnapshot() throws Exception {
        Snapshot snapshot = Snapshot.newBuilder()
                .setServerTick(64)
                .setAckSeq(10)
                .addEntities(EntityState.newBuilder()
                        .setId("p1")
                        .setX(100.5f)
                        .setY(200.5f)
                        .setAngle(1.57f)
                        .setHealth(100)
                        .setIsSelf(true)
                        .build())
                .addBullets(BulletState.newBuilder()
                        .setId("b1")
                        .setOwnerId("p1")
                        .setX(105.0f)
                        .setY(205.0f)
                        .setVx(500.0f)
                        .setVy(0.0f)
                        .build())
                .build();

        ServerMessage serverMessage = ServerMessage.newBuilder()
                .setSnapshot(snapshot)
                .build();

        handler.handleServerMessage(mockSession, serverMessage);

        ArgumentCaptor<TextMessage> textCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(mockSession, atLeastOnce()).sendMessage(textCaptor.capture());

        String payload = textCaptor.getValue().getPayload();
        assertTrue(payload.contains("\"type\":\"snapshot\""));
        assertTrue(payload.contains("\"serverTick\":\"64\"") || payload.contains("\"serverTick\":64"));
        assertTrue(payload.contains("\"ackSeq\":10"));
        assertTrue(payload.contains("\"entities\":["));
        assertTrue(payload.contains("\"bullets\":["));
        assertEquals(1, gatewayMetrics.getTotalSnapshotsReceived());
    }

    @Test
    @DisplayName("handleServerMessage should handle JoinResponse without error")
    void handleServerMessageJoinResponse() {
        ServerMessage joinRespMsg = ServerMessage.newBuilder()
                .setJoinResponse(JoinResponse.newBuilder()
                        .setOk(true)
                        .setSpawnX(500.0f)
                        .setSpawnY(400.0f)
                        .build())
                .build();

        assertDoesNotThrow(() -> handler.handleServerMessage(mockSession, joinRespMsg));
    }

    @Test
    @DisplayName("handleTextMessage should gracefully handle unknown message type")
    void handleUnknownMessageType() {
        String unknownJson = """
                {
                    "type": "unknown_action",
                    "payload": "xyz"
                }
                """;
        assertDoesNotThrow(() -> handler.handleTextMessage(mockSession, new TextMessage(unknownJson)));
    }

    @Test
    @DisplayName("handleTextMessage should gracefully handle malformed JSON")
    void handleMalformedJson() {
        String malformed = "{ this is not valid json }";
        assertDoesNotThrow(() -> handler.handleTextMessage(mockSession, new TextMessage(malformed)));
    }

    @Test
    @DisplayName("handleTransportError should log transport error without throwing")
    void handleTransportError() {
        assertDoesNotThrow(() -> handler.handleTransportError(mockSession, new RuntimeException("socket error")));
    }
}
