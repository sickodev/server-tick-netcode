package com.netcode.gateway.ws.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client message payload representing a discrete user input command frame.
 *
 * <p>Sent by the frontend at the client input tick rate containing movement vectors,
 * aiming orientation, firing trigger state, timestamp, and an incremental sequence number
 * for client-side prediction reconciliation.</p>
 *
 * <p>JSON format example:
 * <pre>{@code
 * {
 *   "type": "user_cmd",
 *   "seq": 1042,
 *   "timestamp": 1724601234567,
 *   "dx": 1.0,
 *   "dy": 0.0,
 *   "aim_angle": 1.5708,
 *   "fire": false
 * }
 * }</pre></p>
 *
 * @param type Protocol message type discriminator, always "user_cmd".
 * @param seq Monotonically increasing sequence number assigned by the client for reconciliation.
 * @param dx Normalized horizontal movement vector (-1.0 left, 1.0 right, 0.0 idle).
 * @param dy Normalized vertical movement vector (-1.0 up, 1.0 down, 0.0 idle).
 * @param aimAngle Aiming direction of the player in radians (supports "aim_angle" and "aimAngle").
 * @param fire Whether the player is firing a bullet during this tick command frame.
 * @param timestamp Client sampling timestamp in epoch milliseconds (optional, defaults to now if absent).
 */
public record UserCmdMessage(
        @JsonProperty(value = "type", defaultValue = "user_cmd") String type,
        @JsonProperty("seq") long seq,
        @JsonProperty("dx") double dx,
        @JsonProperty("dy") double dy,
        @JsonProperty("aim_angle") @JsonAlias("aimAngle") double aimAngle,
        @JsonProperty("fire") boolean fire,
        @JsonProperty("timestamp") Long timestamp
) implements ClientMessage {

    /**
     * Compact constructor ensuring default type discriminator and timestamp if null.
     */
    public UserCmdMessage {
        if (type == null) {
            type = "user_cmd";
        }
        if (timestamp == null || timestamp <= 0) {
            timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Convenience constructor omitting timestamp for backward compatibility and test brevity.
     */
    public UserCmdMessage(String type, long seq, double dx, double dy, double aimAngle, boolean fire) {
        this(type, seq, dx, dy, aimAngle, fire, System.currentTimeMillis());
    }
}
