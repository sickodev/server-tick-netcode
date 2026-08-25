# Agent: Gateway WebSocket
**ID:** `gateway-websocket`
**Emoji:** 🟢
**Complexity:** Straightforward

---

## Mission
Bootstrap the Java gateway as a working Spring Boot app that accepts WebSocket connections and parses incoming JSON messages. This is the public-facing entry point for all browser clients.

---

## Owns
- `gateway/src/main/java/com/netcode/gateway/GatewayApplication.java`
- `gateway/src/main/java/com/netcode/gateway/config/WebSocketConfig.java` *(create)*
- `gateway/src/main/java/com/netcode/gateway/ws/GameWebSocketHandler.java` *(create)*
- `gateway/src/main/java/com/netcode/gateway/ws/dto/` *(create — message DTOs)*
- `gateway/src/main/resources/application.yml`

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| G01 | App starts and exposes a health endpoint | `XS` |
| G02 | Accept WebSocket connections | `S` |
| G03 | Parse incoming JSON messages | `S` |

---

## Skills
- Spring Boot 3 auto-configuration
- Spring WebSocket (`WebSocketHandler`, `WebSocketConfigurer`)
- Jackson `ObjectMapper` (polymorphic deserialization via `type` field)
- CORS configuration for WebSocket endpoints
- Spring `@RestController` for health endpoint

---

## Project Context

### WebSocket Config Pattern
```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler(), "/ws")
                .setAllowedOrigins("http://localhost:5173",
                                   "https://*.vercel.app");
    }
}
```

### Message Parsing (type-based dispatch)
```java
// Use Jackson @JsonSubTypes for polymorphic deserialization
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = JoinMessage.class,    name = "join"),
  @JsonSubTypes.Type(value = UserCmdMessage.class, name = "usercmd")
})
public sealed interface ClientMessage permits JoinMessage, UserCmdMessage {}
```

### DTO Records
```java
public record JoinMessage(String playerId, String name) implements ClientMessage {}

public record UserCmdMessage(
    int seq, long timestamp,
    float dx, float dy,
    float aimAngle, boolean fire
) implements ClientMessage {}
```

### Health Endpoint
```java
@RestController
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

---

## Responsibilities
- WebSocket path is `/ws` — consistent with frontend `.env.local`
- CORS must allow both `localhost:5173` (dev) and `*.vercel.app` (prod)
- `ObjectMapper` is a `@Bean` — never instantiated with `new ObjectMapper()` ad hoc
- Unknown message types → log warning, do not close the session
- Malformed JSON → log error with session ID, do not close the session
- Session ID is `session.getId()` from `WebSocketSession` — use this as the correlation key everywhere

---

## Collaborates With
| Agent | Why |
|---|---|
| `gateway-grpc-bridge` | Hands off `GameWebSocketHandler` for them to add gRPC forwarding |
| `frontend-networking` | Must align on JSON message shapes (field names, `type` values) |

---

## Definition of Done Gate
- [ ] `mvn spring-boot:run` starts in < 5 seconds
- [ ] `GET /health` → `200 { "status": "ok" }`
- [ ] Browser `new WebSocket("ws://localhost:8080/ws")` connects, server logs session ID
- [ ] Valid `join` message parsed correctly (verified by log)
- [ ] Valid `usercmd` message parsed correctly (verified by log)
- [ ] Malformed JSON does not crash the app or close the session
