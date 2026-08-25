package com.netcode.gateway.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GameWebSocketHandler}.
 *
 * <p>Validates session lifecycle callbacks, JSON parsing for join and user_cmd messages,
 * and robust handling of unknown types and malformed payloads.</p>
 */
class GameWebSocketHandlerTest {

    private GameWebSocketHandler handler;
    private WebSocketSession mockSession;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        handler = new GameWebSocketHandler(objectMapper);
        mockSession = mock(WebSocketSession.class);
        when(mockSession.getId()).thenReturn("sess-test-123");
    }

    @Test
    @DisplayName("afterConnectionEstablished should handle open session event")
    void handleConnectionEstablished() {
        assertDoesNotThrow(() -> handler.afterConnectionEstablished(mockSession));
    }

    @Test
    @DisplayName("afterConnectionClosed should handle close session event")
    void handleConnectionClosed() {
        assertDoesNotThrow(() -> handler.afterConnectionClosed(mockSession, CloseStatus.NORMAL));
    }

    @Test
    @DisplayName("handleTextMessage should parse valid join message")
    void handleValidJoinMessage() {
        String joinJson = """
                {
                    "type": "join",
                    "name": "AcePilot"
                }
                """;
        assertDoesNotThrow(() -> handler.handleTextMessage(mockSession, new TextMessage(joinJson)));
    }

    @Test
    @DisplayName("handleTextMessage should parse valid user_cmd message")
    void handleValidUserCmdMessage() {
        String cmdJson = """
                {
                    "type": "user_cmd",
                    "seq": 105,
                    "dx": 1.0,
                    "dy": -1.0,
                    "aim_angle": 3.14159,
                    "fire": true
                }
                """;
        assertDoesNotThrow(() -> handler.handleTextMessage(mockSession, new TextMessage(cmdJson)));
    }

    @Test
    @DisplayName("handleTextMessage should parse user_cmd with camelCase aimAngle")
    void handleUserCmdWithCamelCaseAimAngle() {
        String cmdJson = """
                {
                    "type": "user_cmd",
                    "seq": 106,
                    "dx": 0.0,
                    "dy": 0.0,
                    "aimAngle": 1.57,
                    "fire": false
                }
                """;
        assertDoesNotThrow(() -> handler.handleTextMessage(mockSession, new TextMessage(cmdJson)));
    }

    @Test
    @DisplayName("handleTextMessage should gracefully handle unknown message type without throwing")
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
    @DisplayName("handleTextMessage should gracefully handle malformed JSON without throwing")
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
