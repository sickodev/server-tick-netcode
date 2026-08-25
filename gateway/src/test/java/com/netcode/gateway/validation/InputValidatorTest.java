package com.netcode.gateway.validation;

import com.netcode.gateway.ws.dto.JoinMessage;
import com.netcode.gateway.ws.dto.UserCmdMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link InputValidator}.
 *
 * <p>Validates movement clamping, NaN/Infinity protections, sequence number constraints,
 * HTML stripping, special character removal, and name truncation.</p>
 */
class InputValidatorTest {

    private InputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InputValidator();
    }

    @Test
    @DisplayName("validateAndSanitize should clamp dx and dy within [-1.0, 1.0]")
    void clampMovementAxes() {
        UserCmdMessage excessive = new UserCmdMessage("user_cmd", 10L, 5.5, -3.2, 1.57, false, 12345L);
        UserCmdMessage sanitized = validator.validateAndSanitize(excessive);

        assertEquals(1.0, sanitized.dx(), 0.0001);
        assertEquals(-1.0, sanitized.dy(), 0.0001);
        assertEquals(10L, sanitized.seq());
        assertEquals(1.57, sanitized.aimAngle(), 0.0001);
        assertFalse(sanitized.fire());
    }

    @Test
    @DisplayName("validateAndSanitize should replace NaN and Infinite values with 0.0")
    void handleNonFiniteValues() {
        UserCmdMessage nonFinite = new UserCmdMessage(
                "user_cmd",
                5L,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                true,
                null
        );
        UserCmdMessage sanitized = validator.validateAndSanitize(nonFinite);

        assertEquals(0.0, sanitized.dx(), 0.0001);
        assertEquals(0.0, sanitized.dy(), 0.0001);
        assertEquals(0.0, sanitized.aimAngle(), 0.0001);
        assertEquals(5L, sanitized.seq());
        assertTrue(sanitized.fire());
        assertNotNull(sanitized.timestamp());
    }

    @Test
    @DisplayName("validateAndSanitize should clamp negative sequence numbers to zero")
    void clampNegativeSeq() {
        UserCmdMessage negativeSeq = new UserCmdMessage("user_cmd", -50L, 0.5, 0.5, 0.0, false, 100L);
        UserCmdMessage sanitized = validator.validateAndSanitize(negativeSeq);

        assertEquals(0L, sanitized.seq());
    }

    @Test
    @DisplayName("sanitizePlayerName should sanitize HTML tags, control chars, and truncate to 24 chars")
    void sanitizePlayerNameVariations() {
        // Normal valid name
        assertEquals("PlayerOne", validator.sanitizePlayerName("PlayerOne"));

        // HTML script tag injection
        assertEquals("alertxss", validator.sanitizePlayerName("<script>alert('xss')</script>"));

        // Special characters stripped
        assertEquals("Gamer 123 - pro.x", validator.sanitizePlayerName("Gamer @#$% 123 - pro.x!"));

        // Long name truncated to 24 chars
        String longName = "VeryLongPlayerNameExceedingTwentyFourCharactersTotal";
        String sanitized = validator.sanitizePlayerName(longName);
        assertEquals(24, sanitized.length());
        assertEquals("VeryLongPlayerNameExceed", sanitized);

        // Null and blank defaults
        assertEquals("Anonymous", validator.sanitizePlayerName(null));
        assertEquals("Anonymous", validator.sanitizePlayerName(""));
        assertEquals("Anonymous", validator.sanitizePlayerName("   "));
        assertEquals("Anonymous", validator.sanitizePlayerName("!@#$%^&*()"));
    }

    @Test
    @DisplayName("validateAndSanitize for JoinMessage should produce sanitized record")
    void validateJoinMessage() {
        JoinMessage dirtyJoin = new JoinMessage("join", "<b>Hero</b>", "client-id-123");
        JoinMessage cleanJoin = validator.validateAndSanitize(dirtyJoin);

        assertEquals("Hero", cleanJoin.name());
        assertEquals("client-id-123", cleanJoin.playerId());
    }

    @Test
    @DisplayName("validateAndSanitize with null inputs should return safe defaults")
    void handleNullInputs() {
        UserCmdMessage defaultCmd = validator.validateAndSanitize((UserCmdMessage) null);
        assertNotNull(defaultCmd);
        assertEquals(0L, defaultCmd.seq());
        assertEquals(0.0, defaultCmd.dx(), 0.0001);
        assertEquals(0.0, defaultCmd.dy(), 0.0001);

        JoinMessage defaultJoin = validator.validateAndSanitize((JoinMessage) null);
        assertNotNull(defaultJoin);
        assertEquals("Anonymous", defaultJoin.name());
    }
}
