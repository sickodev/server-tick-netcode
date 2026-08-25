package com.netcode.gateway.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.util.JsonFormat;
import com.netcode.gateway.grpc.GoServiceClient;
import com.netcode.gateway.metrics.GatewayMetrics;
import com.netcode.gateway.proto.ClientMessage;
import com.netcode.gateway.proto.JoinRequest;
import com.netcode.gateway.proto.LeaveRequest;
import com.netcode.gateway.proto.ServerMessage;
import com.netcode.gateway.proto.UserCmd;
import com.netcode.gateway.ratelimit.RateLimiter;
import com.netcode.gateway.session.SessionManager;
import com.netcode.gateway.validation.InputValidator;
import com.netcode.gateway.ws.dto.JoinMessage;
import com.netcode.gateway.ws.dto.UserCmdMessage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/**
 * Primary WebSocket handler responsible for client lifecycle management, incoming JSON parsing,
 * rate limiting, input sanitization, operational metrics tracking, and bridging WebSocket messages
 * to the backend Go authoritative game service via gRPC streams.
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Establishes an isolated bidirectional gRPC stream per connected player upon join.</li>
 *   <li>Assigns server-generated player IDs stored in {@link SessionManager} (never trusting client IDs).</li>
 *   <li>Enforces per-session Token-Bucket rate limiting via {@link RateLimiter} (max 128 cmds/sec).</li>
 *   <li>Validates and clamps player movement and sanitizes player display names via {@link InputValidator}.</li>
 *   <li>Translates incoming WebSocket {@link UserCmdMessage} frames into Protobuf {@link UserCmd} messages.</li>
 *   <li>Converts authoritative server {@link com.netcode.gateway.proto.Snapshot} proto updates into JSON frames.</li>
 *   <li>Collects real-time operational telemetry via {@link GatewayMetrics}.</li>
 *   <li>Handles stream errors and client disconnect teardowns cleanly with {@link LeaveRequest} dispatch.</li>
 * </ul>
 * </p>
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final GoServiceClient goServiceClient;
    private final SessionManager sessionManager;
    private final RateLimiter rateLimiter;
    private final InputValidator inputValidator;
    private final GatewayMetrics gatewayMetrics;

    /**
     * Constructs the WebSocket handler with required infrastructure components.
     *
     * @param objectMapper Shared JSON mapper for client message frames.
     * @param goServiceClient gRPC client bridge to the Go game service.
     * @param sessionManager Registry for active session streams and player IDs.
     * @param rateLimiter Per-session token bucket rate limiter.
     * @param inputValidator Input sanitizer and coordinate clamper.
     * @param gatewayMetrics Real-time telemetry metrics collector.
     */
    public GameWebSocketHandler(
            ObjectMapper objectMapper,
            GoServiceClient goServiceClient,
            SessionManager sessionManager,
            RateLimiter rateLimiter,
            InputValidator inputValidator,
            GatewayMetrics gatewayMetrics
    ) {
        this.objectMapper = objectMapper;
        this.goServiceClient = goServiceClient;
        this.sessionManager = sessionManager;
        this.rateLimiter = rateLimiter;
        this.inputValidator = inputValidator;
        this.gatewayMetrics = gatewayMetrics;
    }

    /**
     * Invoked when a new WebSocket connection is established with a client.
     * Increments active session count in metrics and logs connection event.
     *
     * @param session The newly opened WebSocket session.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        int activeCount = gatewayMetrics.incrementActiveSessions();
        log.info("[ws] session {} connected (active: {})", sessionId, activeCount);
    }

    /**
     * Invoked when an existing WebSocket connection is closed.
     * Dispatches a {@link LeaveRequest} proto to Go, completes gRPC stream, evicts session state
     * from {@link SessionManager}, and cleans up {@link RateLimiter} tracking.
     *
     * @param session The closed WebSocket session.
     * @param status The status reason indicating why the session closed.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        int remainingActive = gatewayMetrics.decrementActiveSessions();
        log.info("[ws] session {} disconnected ({}, active: {})", sessionId, status, remainingActive);

        // Evict rate limiter bucket for disconnected session
        rateLimiter.removeSession(sessionId);

        StreamObserver<ClientMessage> stream = sessionManager.getStream(sessionId);
        String playerId = sessionManager.getPlayerId(sessionId);

        if (stream != null) {
            try {
                if (playerId != null) {
                    // Send LeaveRequest before completing stream (order matters per AGENTS.md)
                    ClientMessage leaveMsg = ClientMessage.newBuilder()
                            .setLeaveRequest(LeaveRequest.newBuilder()
                                    .setPlayerId(playerId)
                                    .build())
                            .build();
                    stream.onNext(leaveMsg);
                }
                stream.onCompleted();
            } catch (Exception e) {
                log.debug("[ws] session {} error completing gRPC stream: {}", sessionId, e.getMessage());
            } finally {
                sessionManager.removeSession(sessionId);
            }
        } else {
            sessionManager.removeSession(sessionId);
        }
    }

    /**
     * Handles incoming text messages over the WebSocket session.
     *
     * <p>Parses the text payload into typed message records. If the JSON is malformed
     * or contains an unknown message type, logs appropriately without disconnecting the client.</p>
     *
     * @param session The WebSocket session transmitting the payload.
     * @param message The raw text message frame received from the client.
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String sessionId = session.getId();
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            if (rootNode == null || !rootNode.isObject()) {
                log.error("[ws] session {} received invalid JSON structure: {}", sessionId, payload);
                return;
            }

            JsonNode typeNode = rootNode.get("type");
            if (typeNode == null || typeNode.isNull() || !typeNode.isTextual()) {
                log.warn("[ws] session {} received message missing 'type' string property: {}", sessionId, payload);
                return;
            }

            String type = typeNode.asText();
            switch (type) {
                case "join" -> {
                    JoinMessage rawJoin = objectMapper.treeToValue(rootNode, JoinMessage.class);
                    JoinMessage sanitizedJoin = inputValidator.validateAndSanitize(rawJoin);
                    handleJoin(session, sanitizedJoin);
                }
                case "user_cmd" -> {
                    UserCmdMessage rawCmd = objectMapper.treeToValue(rootNode, UserCmdMessage.class);
                    UserCmdMessage sanitizedCmd = inputValidator.validateAndSanitize(rawCmd);
                    handleUserCmd(session, sanitizedCmd);
                }
                default -> log.warn("[ws] session {} received unknown message type '{}': {}", sessionId, type, payload);
            }
        } catch (JsonProcessingException e) {
            log.error("[ws] session {} received malformed JSON payload: {}", sessionId, payload, e);
        } catch (Exception e) {
            log.error("[ws] session {} unexpected error while parsing message: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Handles a join request from a WebSocket client.
     *
     * <p>Assigns a server-side player UUID, opens an isolated bidirectional gRPC stream to the
     * Go game service, stores the stream in {@link SessionManager}, and sends an initial {@link JoinRequest}.</p>
     *
     * @param session The client's WebSocket session.
     * @param joinMsg The parsed and sanitized join message DTO.
     */
    private void handleJoin(WebSocketSession session, JoinMessage joinMsg) {
        String sessionId = session.getId();

        if (sessionManager.hasSession(sessionId)) {
            log.warn("[ws] session {} already joined, ignoring duplicate join request", sessionId);
            return;
        }

        String playerId = UUID.randomUUID().toString();
        String playerName = (joinMsg.name() != null && !joinMsg.name().isBlank()) ? joinMsg.name() : "Anonymous";
        log.info("[ws] session {} joining as player {} (name: {})", sessionId, playerId, playerName);

        StreamObserver<ServerMessage> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(ServerMessage serverMessage) {
                handleServerMessage(session, serverMessage);
            }

            @Override
            public void onError(Throwable t) {
                log.error("[grpc] session {} stream error: {}", sessionId, t.getMessage());
                rateLimiter.removeSession(sessionId);
                sessionManager.removeSession(sessionId);
                closeWebSocketQuietly(session, CloseStatus.SERVER_ERROR);
            }

            @Override
            public void onCompleted() {
                log.info("[grpc] session {} stream completed by server", sessionId);
                rateLimiter.removeSession(sessionId);
                sessionManager.removeSession(sessionId);
                closeWebSocketQuietly(session, CloseStatus.NORMAL);
            }
        };

        try {
            StreamObserver<ClientMessage> requestObserver = goServiceClient.play(responseObserver);
            sessionManager.registerSession(sessionId, playerId, requestObserver);

            ClientMessage joinRequestEnvelope = ClientMessage.newBuilder()
                    .setJoinRequest(JoinRequest.newBuilder()
                            .setPlayerId(playerId)
                            .setName(playerName)
                            .build())
                    .build();

            requestObserver.onNext(joinRequestEnvelope);
            gatewayMetrics.recordMessageForwarded();
            log.info("[grpc] session {} sent JoinRequest for playerId {}", sessionId, playerId);
        } catch (Exception e) {
            log.error("[grpc] failed to open gRPC play stream for session {}: {}", sessionId, e.getMessage(), e);
            rateLimiter.removeSession(sessionId);
            sessionManager.removeSession(sessionId);
            closeWebSocketQuietly(session, CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Handles a user input command frame from a WebSocket client.
     *
     * <p>Enforces per-session rate limits, verifies active gRPC stream association, converts the
     * command into a {@link UserCmd} Protobuf message using the authenticated player ID, and forwards
     * it onto the active gRPC stream.</p>
     *
     * @param session The client's WebSocket session.
     * @param cmdMsg The parsed and sanitized user command message DTO.
     */
    private void handleUserCmd(WebSocketSession session, UserCmdMessage cmdMsg) {
        String sessionId = session.getId();

        // 1. Check per-session Token-Bucket rate limit (max 128 cmds/sec)
        if (!rateLimiter.tryAcquire(sessionId)) {
            gatewayMetrics.recordDroppedCommand();
            if (rateLimiter.shouldWarn(sessionId)) {
                log.warn("[rate-limit] session {} exceeding 128 usercmd/sec — dropping", sessionId);
            }
            return;
        }

        // 2. Validate session stream presence
        StreamObserver<ClientMessage> stream = sessionManager.getStream(sessionId);
        String playerId = sessionManager.getPlayerId(sessionId);

        if (stream == null || playerId == null) {
            gatewayMetrics.recordDroppedCommand();
            log.warn("[ws] session {} dropped user_cmd: stream not yet initialized", sessionId);
            return;
        }

        // 3. Assemble Protobuf UserCmd payload with server-authenticated player ID
        long timestamp = (cmdMsg.timestamp() != null && cmdMsg.timestamp() > 0)
                ? cmdMsg.timestamp()
                : System.currentTimeMillis();

        UserCmd protoCmd = UserCmd.newBuilder()
                .setPlayerId(playerId)
                .setSeq((int) cmdMsg.seq())
                .setTimestamp(timestamp)
                .setDx((float) cmdMsg.dx())
                .setDy((float) cmdMsg.dy())
                .setAimAngle((float) cmdMsg.aimAngle())
                .setFire(cmdMsg.fire())
                .build();

        ClientMessage envelope = ClientMessage.newBuilder()
                .setUserCmd(protoCmd)
                .build();

        // 4. Forward message to gRPC stream and measure forwarding latency
        long sendStartNanos = System.nanoTime();
        try {
            stream.onNext(envelope);
            gatewayMetrics.recordMessageForwarded();
            double latencyMs = (System.nanoTime() - sendStartNanos) / 1_000_000.0;
            gatewayMetrics.recordGrpcLatency(latencyMs);
            log.debug("[grpc] session {} forwarded user_cmd seq: {}", sessionId, cmdMsg.seq());
        } catch (Exception e) {
            gatewayMetrics.recordDroppedCommand();
            log.error("[grpc] session {} failed to forward user_cmd seq {}: {}", sessionId, cmdMsg.seq(), e.getMessage());
        }
    }

    /**
     * Processes inbound server messages received from the Go game service over gRPC.
     *
     * <p>Serializes {@link com.netcode.gateway.proto.Snapshot} messages to JSON and delivers them
     * to the associated WebSocket client as text frames with a {@code "type": "snapshot"} discriminator.</p>
     *
     * @param session The recipient's WebSocket session.
     * @param msg The server message received from Go.
     */
    void handleServerMessage(WebSocketSession session, ServerMessage msg) {
        String sessionId = session.getId();
        if (msg.hasJoinResponse()) {
            var joinResp = msg.getJoinResponse();
            log.info("[grpc] session {} received JoinResponse: ok={}, spawn=({}, {})",
                    sessionId, joinResp.getOk(), joinResp.getSpawnX(), joinResp.getSpawnY());
            try {
                String joinJson = String.format(
                        "{\"type\":\"joinResponse\",\"ok\":%b,\"spawnX\":%.2f,\"spawnY\":%.2f,\"spawn_x\":%.2f,\"spawn_y\":%.2f}",
                        joinResp.getOk(), joinResp.getSpawnX(), joinResp.getSpawnY(), joinResp.getSpawnX(), joinResp.getSpawnY());
                if (session.isOpen()) {
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(joinJson));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[grpc] session {} error sending join response: {}", sessionId, e.getMessage(), e);
            }
        }

        if (msg.hasSnapshot()) {
            gatewayMetrics.recordSnapshotReceived();
            try {
                String protoJson = JsonFormat.printer()
                        .includingDefaultValueFields()
                        .omittingInsignificantWhitespace()
                        .print(msg.getSnapshot());

                String wsPayload;
                if ("{}".equals(protoJson)) {
                    wsPayload = "{\"type\":\"snapshot\"}";
                } else {
                    wsPayload = "{\"type\":\"snapshot\"," + protoJson.substring(1);
                }

                if (session.isOpen()) {
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(new TextMessage(wsPayload));
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[grpc] session {} error serializing or sending snapshot: {}", sessionId, e.getMessage(), e);
            }
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

    /**
     * Safely closes a WebSocket session without throwing exceptions.
     *
     * @param session The session to close.
     * @param status The close status code.
     */
    private void closeWebSocketQuietly(WebSocketSession session, CloseStatus status) {
        if (session != null && session.isOpen()) {
            try {
                session.close(status);
            } catch (Exception e) {
                log.debug("[ws] exception while closing session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
