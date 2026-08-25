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

### STORY-G09 · Handle player disconnect and clean up session
**Complexity:** `S`

**Description**
On WebSocket close or error, send a `LeaveRequest` proto on the player's gRPC stream, then cancel the stream and remove the session from `SessionManager`.

**Definition of Done**
- [ ] WebSocket close triggers `LeaveRequest` proto → Go service
- [ ] gRPC stream is cancelled after `LeaveRequest` is sent
- [ ] Session is removed from `SessionManager` map
- [ ] No memory leak: repeated connect/disconnect cycles do not grow the session map
- [ ] Go service log shows player leaving when the browser tab is closed

---

### STORY-G10 · Add per-session rate limiting on `usercmd` messages
**Complexity:** `S`

**Description**
Limit each WebSocket session to a maximum of `MAX_CMDS_PER_SECOND = 128` `usercmd` messages per second (2× the server tick rate as headroom). Drop excess messages and log a warning with the session ID.

**Definition of Done**
- [ ] Sessions sending > 128 `usercmd`/sec have excess messages silently dropped
- [ ] A warning is logged once per second per offending session (not per dropped message)
- [ ] Sessions within the limit are unaffected
- [ ] Rate limiter state is per-session (one session cannot affect another)
- [ ] Rate limiter is reset on reconnect

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
| G09 | Handle player disconnect and clean up session | `S` |
| G10 | Add per-session rate limiting on `usercmd` messages | `S` |
