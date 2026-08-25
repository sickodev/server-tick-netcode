package com.netcode.gateway.config;

import com.netcode.gateway.ws.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring configuration class registering the game WebSocket endpoints and Cross-Origin Resource
 * Sharing (CORS) rules.
 *
 * <p>Enables incoming WebSocket connections to {@code /ws} from local frontend development servers
 * and production deployment domains.</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;

    /**
     * Constructs the WebSocket configuration with the injected game handler.
     *
     * @param gameWebSocketHandler The handler processing game WebSocket sessions and events.
     */
    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
    }

    /**
     * Registers the {@link GameWebSocketHandler} to handle connections at {@code /ws}
     * and sets allowed CORS origin patterns.
     *
     * @param registry The WebSocket handler registry.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("http://localhost:5173", "https://*.vercel.app", "http://localhost:*");
    }
}
