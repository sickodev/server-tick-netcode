# Gateway Stories — Java 21 + Spring Boot 3

Stories are ordered strictly by dependency. Each one should be completable and committable on its own before the next begins.

**Complexity scale:** `XS` < 1 hr · `S` 1–2 hr · `M` 2–4 hr · `L` 4–8 hr

---

## 🏗️ Foundation

---

### STORY-G01 · App starts and exposes a health endpoint
**Complexity:** `XS`

**Description**
Verify the Spring Boot app bootstraps cleanly. Add a single `GET /health` endpoint returning `{ "status": "ok" }`. This is the baseline that every subsequent story depends on.

**Definition of Done**
- [x] `mvn spring-boot:run` starts without errors
- [x] `GET http://localhost:8080/health` returns `200 { "status": "ok" }`
- [x] No unused auto-configurations causing startup warnings
- [x] Application banner updated to show service name (`gateway`)

---

### STORY-G02 · Accept WebSocket connections
**Complexity:** `S`

**Description**
Register a Spring WebSocket handler at the path `/ws`. Log each connect and disconnect event with the session ID. No messages processed yet.

**Definition of Done**
- [x] A browser `new WebSocket("ws://localhost:8080/ws")` connects successfully
- [x] Server logs `[ws] session <id> connected` on open
- [x] Server logs `[ws] session <id> disconnected` on close
- [x] Multiple simultaneous connections are accepted without error
- [x] CORS is configured to allow connections from `http://localhost:5173` (Vite dev server)

---

### STORY-G03 · Parse incoming JSON messages
**Complexity:** `S`

**Description**
Deserialise incoming WebSocket text frames into typed Java records. Define `JoinMessage` and `UserCmdMessage` records matching the frontend JSON contract. Log the parsed type and `seq` to console.

**Definition of Done**
- [x] `{ "type": "join", "playerId": "...", "name": "..." }` deserialises to `JoinMessage`
- [x] `{ "type": "usercmd", "seq": 1, ... }` deserialises to `UserCmdMessage`
- [x] Unknown message types are logged as a warning and silently dropped (no crash)
- [x] Malformed JSON is caught, logged as error, and the session remains open
- [x] Jackson `ObjectMapper` is configured as a Spring bean (not instantiated ad hoc)

---

## 🔌 gRPC Bridge

---

### STORY-G04 · Add the shared Protobuf definition and generate Java stubs
**Complexity:** `S`

**Description**
Copy `game.proto` into `src/main/proto/`. Run `mvn generate-sources` to confirm the `protobuf-maven-plugin` produces Java classes under `target/generated-sources`. No runtime usage yet.

**Definition of Done**
- [x] `game.proto` lives at `src/main/proto/game.proto`
- [x] `mvn generate-sources` completes without errors
- [x] Generated classes (`UserCmd`, `Snapshot`, `GameServiceGrpc`, etc.) exist under `target/generated-sources`
- [x] Generated sources are excluded from `.gitignore` (not committed)
- [x] `mvn clean package` produces a runnable fat JAR

---

### STORY-G05 · Connect to the Go service via gRPC
**Complexity:** `S`

**Description**
Create `GoServiceClient.java` — a Spring `@Component` that opens a gRPC `ManagedChannel` to the Go service on startup using the `go-service.host` and `go-service.port` values from `application.yml`. Log connection state changes.

**Definition of Done**
- [x] `GoServiceClient` is a Spring-managed singleton
- [x] Host and port are injected from `application.yml` (no hardcoding)
- [x] Channel is created with `ManagedChannelBuilder.forAddress(host, port).usePlaintext()`
- [x] On startup, logs `[grpc] channel created → <host>:<port>`
- [x] On application shutdown (`@PreDestroy`), channel is gracefully shut down
- [x] App starts cleanly even if the Go service is not yet running (channel is lazy)

---

### STORY-G06 · Open a bidirectional gRPC stream per player
**Complexity:** `M`

**Description**
When a `JoinMessage` arrives on WebSocket, open a bidirectional `GameService/Play` gRPC stream for that player. Store the mapping `sessionId → StreamObserver<ClientMessage>`. Send a `JoinRequest` proto message as the first message on the stream.

**Definition of Done**
- [x] Each WebSocket session gets exactly one gRPC stream (created on join)
- [x] `JoinRequest` proto is sent immediately on stream open
- [x] `SessionManager.java` holds the `sessionId → StreamObserver` map in a `ConcurrentHashMap`
- [x] If the gRPC stream errors, log the error and close the corresponding WebSocket session
- [x] Duplicate join messages from the same session are ignored

---

### STORY-G07 · Forward `usercmd` messages to the Go service
**Complexity:** `S`

**Description**
When a `UserCmdMessage` arrives on WebSocket, convert it to a `UserCmd` proto, wrap it in a `ClientMessage`, and send it on the player's gRPC stream.

**Definition of Done**
- [x] Every received `UserCmdMessage` is forwarded to the Go service within the same thread/handler
- [x] Proto field mapping matches the JSON field names (`seq`, `dx`, `dy`, `aimAngle`, `fire`, `timestamp`)
- [x] `playerId` is injected from the session (not trusted from the client message)
- [x] Forwarding fails gracefully if the gRPC stream is not yet open (logs warning, drops message)
- [x] No message reordering: commands are forwarded in the order they arrive

---

### STORY-G08 · Receive snapshots from Go and push to WebSocket client
**Complexity:** `M`

**Description**
Implement the `onNext` handler of the gRPC `StreamObserver<ServerMessage>`. When a `Snapshot` proto arrives, serialise it to JSON and push it as a WebSocket text frame to the correct session.

**Definition of Done**
- [x] Each `Snapshot` proto received from Go is serialised using `JsonFormat.printer()` and sent to the corresponding WebSocket session
- [x] Serialisation uses the proto-to-JSON mapping (field names match what the frontend expects)
- [x] If the WebSocket session is closed, the gRPC stream is cancelled and the session is cleaned up
- [x] No snapshot is sent to the wrong session
- [x] End-to-end test: browser console shows snapshot messages arriving at ~64 per second

---

## 🛡️ Resilience

---

### STORY-G09 · Token-Bucket Rate Limiter & Disconnect Cleanup
### STORY-G09 · Token-Bucket Rate Limiter & Disconnect Cleanup
**Complexity:** `S`

**Description**
Implement token-bucket rate limiter per WebSocket session (max 128 commands/sec, burst capacity 16). In `GameWebSocketHandler.handleUserCmd`, check rate limit, drop command if exceeded, log warning `[rate-limit]` with 1-second throttle, and increment dropped command metric. On WebSocket close or error, send `LeaveRequest` proto on the player's gRPC stream, cancel/complete stream, and remove session from `SessionManager` and `RateLimiter`.

**Definition of Done**
- [x] Per-session Token-Bucket rate limiter enforcing max 128 cmds/sec with 16-burst capacity in `RateLimiter.java`
- [x] Sessions sending > 128 `usercmd`/sec have excess messages silently dropped
- [x] Rate-limit warning logged at most once per second per offending session (`[rate-limit]`)
- [x] WebSocket close triggers `LeaveRequest` proto → Go service
- [x] gRPC stream is completed after `LeaveRequest` is sent
- [x] Session state removed cleanly from `SessionManager` and `RateLimiter`
- [x] No memory leak: repeated connect/disconnect cycles do not grow session or bucket maps

---

### STORY-G10 · Input Validation & Sanitization
**Complexity:** `S`

**Description**
In `com.netcode.gateway.validation.InputValidator`, validate and sanitize `UserCmdMessage` and `JoinMessage`. Clamp `dx` and `dy` movement axes to `[-1.0, 1.0]`. Ensure non-negative `seq >= 0` and finite float values for `aimAngle` (preventing NaN/Infinity poisoning). Sanitize player display names by removing HTML tags, stripping disallowed special characters, and truncating to 24 characters.

**Definition of Done**
- [x] `InputValidator.java` validates and sanitizes `UserCmdMessage` and `JoinMessage`
- [x] `dx` and `dy` clamped to `[-1.0, 1.0]` with NaN/Infinite checks
- [x] Sequence number constrained to `seq >= 0`
- [x] Aim angle sanitized to finite numbers
- [x] Player name stripped of HTML tags, control/disallowed special characters, and truncated to 24 chars
- [x] Clean unit tests in `InputValidatorTest.java` verifying all validation and sanitization invariants

---

### STORY-G11 · Connection Metrics & Monitoring
**Complexity:** `S`

**Description**
In `com.netcode.gateway.metrics.GatewayMetrics`, track active WebSocket sessions, total messages forwarded, dropped commands, total snapshots received, and gRPC forwarding latency. Expose metrics in `GET /health` and `GET /actuator/metrics`.

**Definition of Done**
- [x] Thread-safe `GatewayMetrics.java` tracking active sessions, forwarded messages, dropped commands, and gRPC latency
- [x] Metrics integrated into `GameWebSocketHandler` lifecycle and message flows
- [x] Metrics exposed in `GET /health` and `GET /actuator/metrics` endpoints in `HealthController.java`
- [x] Clean unit tests in `GatewayMetricsTest.java` and `HealthControllerTest.java`

---

## Summary

| Story | Title | Complexity |
|---|---|---|
| G01 | App starts and exposes a health endpoint | `XS` |
| G02 | Accept WebSocket connections | `S` |
| G03 | Parse incoming JSON messages | `S` |
| G04 | Add shared Protobuf definition and generate Java stubs | `S` |
| G05 | Connect to the Go service via gRPC | `S` |
| G06 | Open a bidirectional gRPC stream per player | `M` |
| G07 | Forward `usercmd` messages to the Go service | `S` |
| G08 | Receive snapshots from Go and push to WebSocket client | `M` |
| G09 | Token-Bucket Rate Limiter & Disconnect Cleanup | `S` |
| G10 | Input Validation & Sanitization | `S` |
| G11 | Connection Metrics & Monitoring | `S` |
