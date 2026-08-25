package com.netcode.gateway.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netcode.gateway.ws.dto.ClientMessage;
import com.netcode.gateway.ws.dto.JoinMessage;
import com.netcode.gateway.ws.dto.UserCmdMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Primary WebSocket handler responsible for lifecycle management and incoming message parsing.
 *
 * <p>Maintains active WebSocket client sessions, parses incoming JSON text payloads into typed
 * {@link ClientMessage} records, and logs events with standard {@code [ws]} context prefixes.</p>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;

    /**
     * Constructs the WebSocket handler with an injected Jackson {@link ObjectMapper}.
     *
     * @param objectMapper Shared JSON object mapper for deserializing client messages.
     */
    public GameWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Invoked when a new WebSocket connection is established with a client.
     *
     * @param session The newly opened WebSocket session.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[ws] session {} connected", session.getId());
    }

    /**
     * Invoked when an existing WebSocket connection is closed.
     *
     * @param session The closed WebSocket session.
     * @param status The status reason indicating why the session closed.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[ws] session {} disconnected", session.getId());
    }

    /**
     * Handles incoming text messages over the WebSocket session.
     *
     * <p>Parses the text payload into a {@link ClientMessage}. If the JSON is malformed
     * or contains an unknown message type, logs appropriately without disconnecting the client.</p>
     *
     * @param session The WebSocket session transmitting the payload.
     * @param message The raw text message frame received from the client.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            // First inspect message root to identify type and ensure graceful error handling
            JsonNode rootNode = objectMapper.readTree(payload);
            if (rootNode == null || !rootNode.isObject()) {
                log.error("[ws] session {} received invalid JSON structure: {}", session.getId(), payload);
                return;
            }

            JsonNode typeNode = rootNode.get("type");
            if (typeNode == null || typeNode.isNull() || !typeNode.isTextual()) {
                log.warn("[ws] session {} received message missing 'type' string property: {}", session.getId(), payload);
                return;
            }

            String type = typeNode.asText();
            switch (type) {
                case "join" -> {
                    JoinMessage joinMsg = objectMapper.treeToValue(rootNode, JoinMessage.class);
                    log.info("[ws] session {} parsed message type: join, player name: {}", session.getId(), joinMsg.name());
                }
                case "user_cmd" -> {
                    UserCmdMessage cmdMsg = objectMapper.treeToValue(rootNode, UserCmdMessage.class);
                    log.info("[ws] session {} parsed message type: user_cmd, seq: {}", session.getId(), cmdMsg.seq());
                }
                default -> log.warn("[ws] session {} received unknown message type '{}': {}", session.getId(), type, payload);
            }
        } catch (JsonProcessingException e) {
            // Malformed JSON should be logged as error while keeping the session open
            log.error("[ws] session {} received malformed JSON payload: {}", session.getId(), payload, e);
        } catch (Exception e) {
            log.error("[ws] session {} unexpected error while parsing message: {}", session.getId(), e.getMessage(), e);
        }
    }

    /**
     * Invoked when a transport-level error occurs on the underlying socket.
     *
     * @param session The WebSocket session where the error occurred.
     * @param exception The transport exception that was raised.
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[ws] transport error on session {}: {}", session.getId(), exception.getMessage(), exception);
    }
}
