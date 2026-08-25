package com.netcode.gateway.ws.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Client message payload requesting to join the multiplayer game arena.
 *
 * <p>JSON format example:
 * <pre>{@code
 * {
 *   "type": "join",
 *   "name": "PlayerOne"
 * }
 * }</pre></p>
 *
 * @param type Protocol message type discriminator, always "join".
 * @param name Display name chosen by the player.
 */
public record JoinMessage(
        @JsonProperty(value = "type", defaultValue = "join") String type,
        @JsonProperty("name") String name
) implements ClientMessage {

    /**
     * Compact constructor ensuring default type discriminator if null.
     */
    public JoinMessage {
        if (type == null) {
            type = "join";
        }
    }
}
