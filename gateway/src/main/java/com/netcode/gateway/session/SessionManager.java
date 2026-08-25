package com.netcode.gateway.session;

import com.netcode.gateway.proto.ClientMessage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe manager responsible for associating WebSocket sessions with active gRPC streams
 * and unique player identifiers.
 *
 * <p>Guarantees that each connected WebSocket client has an isolated gRPC stream and ensures
 * that player IDs are strictly resolved server-side from internal session state rather than
 * untrusted client message bodies.</p>
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    /**
     * Map of WebSocket session ID to outbound gRPC ClientMessage stream observer.
     */
    private final ConcurrentHashMap<String, StreamObserver<ClientMessage>> streams = new ConcurrentHashMap<>();

    /**
     * Map of WebSocket session ID to server-generated unique player ID.
     */
    private final ConcurrentHashMap<String, String> playerIds = new ConcurrentHashMap<>();

    /**
     * Reverse lookup map: Player ID to WebSocket session ID.
     */
    private final ConcurrentHashMap<String, String> sessionByPlayerId = new ConcurrentHashMap<>();

    /**
     * Registers a new active session mapping WebSocket session ID to player ID and gRPC stream.
     *
     * @param sessionId The unique identifier of the WebSocket session.
     * @param playerId The server-assigned UUID for the player.
     * @param stream The outbound gRPC stream observer for sending client envelopes to Go.
     */
    public void registerSession(String sessionId, String playerId, StreamObserver<ClientMessage> stream) {
        streams.put(sessionId, stream);
        playerIds.put(sessionId, playerId);
        sessionByPlayerId.put(playerId, sessionId);
        log.info("[session] registered session {} -> player {} (active: {})", sessionId, playerId, streams.size());
    }

    /**
     * Retrieves the outbound gRPC stream associated with a given WebSocket session ID.
     *
     * @param sessionId The WebSocket session identifier.
     * @return The gRPC {@link StreamObserver} if present, or {@code null} if not registered.
     */
    public StreamObserver<ClientMessage> getStream(String sessionId) {
        return streams.get(sessionId);
    }

    /**
     * Retrieves the server-assigned player ID for a given WebSocket session ID.
     *
     * @param sessionId The WebSocket session identifier.
     * @return The player UUID string if present, or {@code null} if not registered.
     */
    public String getPlayerId(String sessionId) {
        return playerIds.get(sessionId);
    }

    /**
     * Checks if a session is currently registered and has an active gRPC stream mapping.
     *
     * @param sessionId The WebSocket session identifier.
     * @return {@code true} if the session is registered, {@code false} otherwise.
     */
    public boolean hasSession(String sessionId) {
        return streams.containsKey(sessionId);
    }

    /**
     * Removes and cleans up all state associated with a WebSocket session ID.
     *
     * @param sessionId The WebSocket session identifier.
     */
    public void removeSession(String sessionId) {
        StreamObserver<ClientMessage> stream = streams.remove(sessionId);
        String playerId = playerIds.remove(sessionId);
        if (playerId != null) {
            sessionByPlayerId.remove(playerId);
        }
        log.info("[session] removed session {} (player: {}, active: {})", sessionId, playerId, streams.size());
    }

    /**
     * Returns the total count of active registered sessions.
     *
     * @return Active session count.
     */
    public int getActiveSessionCount() {
        return streams.size();
    }

    /**
     * Returns an unmodifiable view of the current session-to-player mappings.
     *
     * @return Map of session IDs to player IDs.
     */
    public Map<String, String> getAllPlayerIds() {
        return Collections.unmodifiableMap(playerIds);
    }

    /**
     * Clears all registered sessions (primarily for testing lifecycle teardown).
     */
    public void clear() {
        streams.clear();
        playerIds.clear();
        sessionByPlayerId.clear();
    }
}
