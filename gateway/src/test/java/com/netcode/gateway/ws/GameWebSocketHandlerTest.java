package com.netcode.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcode.gateway.grpc.GoServiceClient;
import com.netcode.gateway.proto.BulletState;
import com.netcode.gateway.proto.ClientMessage;
import com.netcode.gateway.proto.EntityState;
import com.netcode.gateway.proto.JoinResponse;
import com.netcode.gateway.proto.ServerMessage;
import com.netcode.gateway.proto.Snapshot;
import com.netcode.gateway.session.SessionManager;
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
 * <p>Validates WebSocket lifecycle, join negotiation, bidirectional gRPC stream opening,
 * user_cmd forwarding with session player ID injection, snapshot JSON delivery, and error handling.</p>
 */
class GameWebSocketHandlerTest {

    private ObjectMapper objectMapper;
    private GoServiceClient mockGoServiceClient;
    private SessionManager sessionManager;
    private GameWebSocketHandler handler;
    private WebSocketSession mockSession;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockGoServiceClient = mock(GoServiceClient.class);
        sessionManager = new SessionManager();
        handler = new GameWebSocketHandler(objectMapper, mockGoServiceClient, sessionManager);

        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn("sess-test-123");
        when(mockSession.isOpen()).thenReturn(true);
    }

    @Test
    @DisplayName("afterConnectionEstablished should handle open session event")
    void handleConnectionEstablished() {
        assertDoesNotThrow(() -> handler.afterConnectionEstablished(mockSession));
    }

    @Test
    @DisplayName("afterConnectionClosed should clean up session and send LeaveRequest if stream exists")
    void handleConnectionClosedWithActiveStream() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        sessionManager.registerSession("sess-test-123", "player-abc", mockStream);

        handler.afterConnectionClosed(mockSession, CloseStatus.NORMAL);

        ArgumentCaptor<ClientMessage> captor = ArgumentCaptor.forClass(ClientMessage.class);
        verify(mockStream).onNext(captor.capture());
        verify(mockStream).onCompleted();

        ClientMessage sent = captor.getValue();
        assertTrue(sent.hasLeaveRequest());
        assertEquals("player-abc", sent.getLeaveRequest().getPlayerId());
        assertFalse(sessionManager.hasSession("sess-test-123"));
    }

    @Test
    @DisplayName("handleTextMessage with join should open gRPC stream and send JoinRequest")
    void handleValidJoinMessage() {
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

        verify(mockGoServiceClient, times(1)).play(any());
        assertTrue(sessionManager.hasSession("sess-test-123"));
        String playerId = sessionManager.getPlayerId("sess-test-123");
        assertNotNull(playerId);

        ArgumentCaptor<ClientMessage> captor = ArgumentCaptor.forClass(ClientMessage.class);
        verify(mockStream, times(1)).onNext(captor.capture());

        ClientMessage sent = captor.getValue();
        assertTrue(sent.hasJoinRequest());
        assertEquals("AcePilot", sent.getJoinRequest().getName());
        assertEquals(playerId, sent.getJoinRequest().getPlayerId());
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
    @DisplayName("handleTextMessage with user_cmd should forward command to gRPC stream with session playerId")
    void handleValidUserCmdMessage() {
        @SuppressWarnings("unchecked")
        StreamObserver<ClientMessage> mockStream = mock(StreamObserver.class);
        sessionManager.registerSession("sess-test-123", "verified-player-1", mockStream);

        String cmdJson = """
                {
                    "type": "user_cmd",
                    "seq": 105,
                    "timestamp": 1724601234567,
                    "dx": 1.0,
                    "dy": -1.0,
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
        assertEquals(1.0f, sent.getUserCmd().getDx(), 0.001f);
        assertEquals(-1.0f, sent.getUserCmd().getDy(), 0.001f);
        assertEquals(3.14159f, sent.getUserCmd().getAimAngle(), 0.001f);
        assertTrue(sent.getUserCmd().getFire());
    }

    @Test
    @DisplayName("handleTextMessage with user_cmd before join should be dropped gracefully")
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
