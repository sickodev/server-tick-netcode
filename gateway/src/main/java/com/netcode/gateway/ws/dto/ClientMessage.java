package com.netcode.gateway.ws.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base sealed interface representing all incoming JSON messages sent by a client over WebSocket.
 *
 * <p>Uses Jackson polymorphic type handling via the {@code "type"} property in JSON payloads
 * to deserialize into concrete record subtypes (e.g., {@link JoinMessage}, {@link UserCmdMessage}).</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = JoinMessage.class, name = "join"),
        @JsonSubTypes.Type(value = UserCmdMessage.class, name = "user_cmd")
})
public sealed interface ClientMessage permits JoinMessage, UserCmdMessage {

    /**
     * Returns the protocol message discriminator string.
     *
     * @return The message type string (e.g., "join", "user_cmd").
     */
    String type();
}
