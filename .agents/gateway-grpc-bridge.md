# Agent: Gateway gRPC Bridge
**ID:** `gateway-grpc-bridge`
**Emoji:** 🟡
**Complexity:** Moderate

---

## Mission
Wire the gateway's WebSocket sessions to the Go game service via bidirectional gRPC streams. One stream per player. Messages flow in both directions: `usercmd` → Go, `Snapshot` ← Go → WebSocket.

---

## Owns
- `gateway/src/main/java/com/netcode/gateway/grpc/GoServiceClient.java` *(create)*
- `gateway/src/main/java/com/netcode/gateway/session/SessionManager.java` *(create)*
- `gateway/src/main/proto/game.proto` *(copy from service/proto)*
- Modifications to `GameWebSocketHandler.java` (add gRPC forwarding)

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| G04 | Add shared Protobuf definition and generate Java stubs | `S` |
| G05 | Connect to the Go service via gRPC | `S` |
| G06 | Open a bidirectional gRPC stream per player | `M` |
| G07 | Forward `usercmd` messages to the Go service | `S` |
| G08 | Receive snapshots from Go and push to WebSocket client | `M` |

---

## Skills
- gRPC Java (`ManagedChannel`, `ManagedChannelBuilder`, `StreamObserver`)
- Protobuf Java API (`MessageOrBuilder`, `JsonFormat.printer()`)
- Thread safety (`ConcurrentHashMap`, careful use of `synchronized`)
- Spring `@Component` lifecycle (`@PostConstruct`, `@PreDestroy`)
- Bidirectional streaming gRPC pattern

---

## Project Context

### gRPC Channel Setup
```java
@Component
public class GoServiceClient {
    private ManagedChannel channel;

    @PostConstruct
    public void init() {
        channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build();
        stub = GameServiceGrpc.newStub(channel); // async stub
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

### Session Manager
```java
// sessionId → the outbound StreamObserver to send UserCmd to Go
private final ConcurrentHashMap<String, StreamObserver<ClientMessage>> streams
    = new ConcurrentHashMap<>();
```

### Opening a Stream (on JoinMessage)
```java
StreamObserver<ServerMessage> responseObserver = new StreamObserver<>() {
    @Override public void onNext(ServerMessage msg) {
        // Convert Snapshot proto → JSON → push to WebSocket
        String json = JsonFormat.printer().print(msg.getSnapshot());
        webSocketSession.sendMessage(new TextMessage(json));
    }
    @Override public void onError(Throwable t) { /* cleanup */ }
    @Override public void onCompleted() { /* cleanup */ }
};
StreamObserver<ClientMessage> requestObserver = stub.play(responseObserver);
sessions.put(sessionId, requestObserver);

// Send JoinRequest as first message
requestObserver.onNext(ClientMessage.newBuilder()
    .setJoinRequest(JoinRequest.newBuilder()
        .setPlayerId(playerId).setName(name).build())
    .build());
```

### Forwarding UserCmd
```java
UserCmd proto = UserCmd.newBuilder()
    .setPlayerId(sessionPlayerId)  // ← from session, NOT from client message
    .setSeq(msg.seq())
    .setTimestamp(msg.timestamp())
    .setDx(msg.dx()).setDy(msg.dy())
    .setAimAngle(msg.aimAngle())
    .setFire(msg.fire())
    .build();
requestObserver.onNext(ClientMessage.newBuilder().setUserCmd(proto).build());
```

### Snapshot → JSON
```java
// Use proto's JsonFormat for field-name-correct JSON
String json = JsonFormat.printer()
    .includingDefaultValueFields()
    .print(snapshot);
session.sendMessage(new TextMessage("{\"type\":\"snapshot\"," + json.substring(1)));
// Prefix the type field — JsonFormat doesn't include it
```

---

## Responsibilities
- `playerId` is **always** sourced from the session map — never from the client's own messages
- One gRPC stream per WebSocket session — never share streams between players
- `onError` on the gRPC stream must close the corresponding WebSocket session
- `SessionManager` is the only class that holds the `sessionId → stream` map
- Proto must be identical copy of `service/proto/game.proto`

---

## Collaborates With
| Agent | Why |
|---|---|
| `proto-contract` | Depends on compiled proto stubs — must complete G04 first |
| `gateway-websocket` | Extends `GameWebSocketHandler` with gRPC forwarding |
| `service-networking` | Integration handshake: gateway sends join, service responds |
| `gateway-resilience` | Hands off `SessionManager` for disconnect cleanup |

---

## Definition of Done Gate
- [ ] `mvn generate-sources` produces `GameServiceGrpc.java` and all proto message classes
- [ ] Gateway connects to Go service on startup (channel state logged)
- [ ] Browser join → `JoinRequest` arrives at Go service (verified by Go log)
- [ ] `usercmd` from browser → Go service within the same request cycle
- [ ] Snapshot from Go → browser (verified in browser DevTools WebSocket tab)
- [ ] gRPC stream error closes the WebSocket session cleanly
