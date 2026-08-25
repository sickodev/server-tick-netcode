package com.netcode.gateway.ws.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client message payload requesting to join the multiplayer game arena.
 *
 * <p>JSON format example:
 * <pre>{@code
 * {
 *   "type": "join",
 *   "playerId": "a3ce8016-cf3a-4799-b5b6-a8401b7b822a",
 *   "name": "PlayerOne"
 * }
 * }</pre></p>
 *
 * @param type Protocol message type discriminator, always "join".
 * @param name Display name chosen by the player.
 * @param playerId Optional client-provided session identifier (ignored by server for security).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JoinMessage(
        @JsonProperty(value = "type", defaultValue = "join") String type,
        @JsonProperty("name") String name,
        @JsonProperty("playerId") @JsonAlias("player_id") String playerId
) implements ClientMessage {

    /**
     * Compact constructor ensuring default type discriminator if null.
     */
    public JoinMessage {
        if (type == null) {
            type = "join";
        }
    }

    /**
     * Convenience constructor omitting playerId for backward compatibility.
     */
    public JoinMessage(String type, String name) {
        this(type, name, null);
    }
}
