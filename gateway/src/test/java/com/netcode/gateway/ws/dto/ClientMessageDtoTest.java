package com.netcode.gateway.ws.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying JSON serialization and polymorphic deserialization of {@link ClientMessage} DTOs.
 */
class ClientMessageDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Polymorphic deserialization of 'join' message into JoinMessage")
    void deserializeJoinMessage() throws Exception {
        String json = """
                {
                    "type": "join",
                    "name": "Strikefighter"
                }
                """;

        ClientMessage msg = objectMapper.readValue(json, ClientMessage.class);
        assertInstanceOf(JoinMessage.class, msg);

        JoinMessage joinMsg = (JoinMessage) msg;
        assertEquals("join", joinMsg.type());
        assertEquals("Strikefighter", joinMsg.name());
    }

    @Test
    @DisplayName("Polymorphic deserialization of 'user_cmd' message into UserCmdMessage")
    void deserializeUserCmdMessage() throws Exception {
        String json = """
                {
                    "type": "user_cmd",
                    "seq": 42,
                    "dx": 0.5,
                    "dy": -0.5,
                    "aim_angle": 1.57,
                    "fire": true
                }
                """;

        ClientMessage msg = objectMapper.readValue(json, ClientMessage.class);
        assertInstanceOf(UserCmdMessage.class, msg);

        UserCmdMessage cmdMsg = (UserCmdMessage) msg;
        assertEquals("user_cmd", cmdMsg.type());
        assertEquals(42L, cmdMsg.seq());
        assertEquals(0.5, cmdMsg.dx());
        assertEquals(-0.5, cmdMsg.dy());
        assertEquals(1.57, cmdMsg.aimAngle());
        assertTrue(cmdMsg.fire());
    }
}
