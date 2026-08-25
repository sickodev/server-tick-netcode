package com.netcode.gateway.validation;

import com.netcode.gateway.ws.dto.JoinMessage;
import com.netcode.gateway.ws.dto.UserCmdMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Validates and sanitizes client-supplied input payloads received over WebSocket frames.
 *
 * <p>Enforces strict physical constraints and security hygiene before inputs are translated
 * into Protobuf envelopes and forwarded to the authoritative Go simulation loop:</p>
 * <ul>
 *   <li>Movement inputs ({@code dx}, {@code dy}) clamped within normalized bounds [-1.0, 1.0].</li>
 *   <li>Aim angles checked for finite numeric representation (preventing NaN / Infinity poisoning).</li>
 *   <li>Sequence numbers constrained to non-negative integers ({@code seq >= 0}).</li>
 *   <li>Player display names stripped of HTML tags, control characters, and truncated to safe length.</li>
 * </ul>
 */
@Component
public class InputValidator {

    private static final Logger log = LoggerFactory.getLogger(InputValidator.class);

    /**
     * Minimum valid normalized movement axis value.
     */
    public static final double MIN_MOVE_AXIS = -1.0;

    /**
     * Maximum valid normalized movement axis value.
     */
    public static final double MAX_MOVE_AXIS = 1.0;

    /**
     * Maximum allowed character length for sanitized player display names.
     */
    public static final int MAX_PLAYER_NAME_LENGTH = 24;

    /**
     * Fallback player display name when input is missing or empty after sanitization.
     */
    public static final String DEFAULT_PLAYER_NAME = "Anonymous";

    /**
     * Pattern matching HTML tags for removal.
     */
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");

    /**
     * Pattern matching characters that are NOT alphanumeric, spaces, hyphens, underscores, or periods.
     */
    private static final Pattern DISALLOWED_NAME_CHARS_PATTERN = Pattern.compile("[^a-zA-Z0-9 _.-]");

    /**
     * Validates and sanitizes an incoming {@link UserCmdMessage}, returning a sanitized copy.
     *
     * @param cmd Raw user command message from the WebSocket client.
     * @return Sanitized {@link UserCmdMessage} with clamped and verified numeric fields.
     */
    public UserCmdMessage validateAndSanitize(UserCmdMessage cmd) {
        if (cmd == null) {
            return new UserCmdMessage("user_cmd", 0L, 0.0, 0.0, 0.0, false, System.currentTimeMillis());
        }

        // 1. Constrain sequence number to non-negative range
        long sanitizedSeq = Math.max(0, cmd.seq());

        // 2. Sanitize and clamp movement axes dx and dy to [-1.0, 1.0]
        double sanitizedDx = clampAxis(cmd.dx());
        double sanitizedDy = clampAxis(cmd.dy());

        // 3. Ensure aim angle is finite float/double
        double sanitizedAimAngle = sanitizeAngle(cmd.aimAngle());

        // 4. Ensure non-null timestamp
        Long timestamp = (cmd.timestamp() != null && cmd.timestamp() > 0)
                ? cmd.timestamp()
                : System.currentTimeMillis();

        return new UserCmdMessage(
                "user_cmd",
                sanitizedSeq,
                sanitizedDx,
                sanitizedDy,
                sanitizedAimAngle,
                cmd.fire(),
                timestamp
        );
    }

    /**
     * Validates and sanitizes an incoming {@link JoinMessage}, cleaning the player display name.
     *
     * @param join Raw join message from the WebSocket client.
     * @return Sanitized {@link JoinMessage} with clean player name.
     */
    public JoinMessage validateAndSanitize(JoinMessage join) {
        if (join == null) {
            return new JoinMessage("join", DEFAULT_PLAYER_NAME, null);
        }
        String sanitizedName = sanitizePlayerName(join.name());
        return new JoinMessage("join", sanitizedName, join.playerId());
    }

    /**
     * Sanitizes player display name by stripping HTML tags, removing disallowed special characters,
     * trimming whitespace, and truncating to {@link #MAX_PLAYER_NAME_LENGTH}.
     *
     * @param rawName Raw player name input string.
     * @return Sanitized safe player name string.
     */
    public String sanitizePlayerName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return DEFAULT_PLAYER_NAME;
        }

        // Strip HTML markup tags (prevent XSS / injection attacks)
        String cleaned = HTML_TAG_PATTERN.matcher(rawName).replaceAll("");

        // Strip disallowed special characters, keeping alphanumeric, spaces, dashes, dots, underscores
        cleaned = DISALLOWED_NAME_CHARS_PATTERN.matcher(cleaned).replaceAll("");

        // Normalize contiguous whitespaces and trim edges
        cleaned = cleaned.trim().replaceAll("\\s+", " ");

        if (cleaned.isBlank()) {
            return DEFAULT_PLAYER_NAME;
        }

        // Truncate to maximum permissible character length
        if (cleaned.length() > MAX_PLAYER_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_PLAYER_NAME_LENGTH);
        }

        return cleaned;
    }

    /**
     * Clamps a directional movement axis to the [-1.0, 1.0] interval, replacing non-finite values with 0.0.
     *
     * @param value Raw axis value.
     * @return Clamped finite value within [-1.0, 1.0].
     */
    private double clampAxis(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(MIN_MOVE_AXIS, Math.min(MAX_MOVE_AXIS, value));
    }

    /**
     * Sanitizes an aim angle value, replacing non-finite values (NaN / Infinity) with 0.0.
     *
     * @param angle Raw angle value in radians.
     * @return Finite angle value.
     */
    private double sanitizeAngle(double angle) {
        if (Double.isNaN(angle) || Double.isInfinite(angle)) {
            return 0.0;
        }
        return angle;
    }
}
