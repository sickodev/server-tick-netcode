# Agent: Gateway Resilience
**ID:** `gateway-resilience`
**Emoji:** 🟢
**Complexity:** Straightforward

---

## Mission
Harden the gateway against common failure modes: clean player disconnect handling and rate limiting to prevent command flooding. These are the last gateway stories before the service is production-ready.

---

## Owns
- Modifications to `GameWebSocketHandler.java` (afterConnectionClosed hook)
- `gateway/src/main/java/com/netcode/gateway/ws/RateLimiter.java` *(create)*
- Modifications to `SessionManager.java` (cleanup logic)

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| G09 | Handle player disconnect and clean up session | `S` |
| G10 | Add per-session rate limiting on `usercmd` messages | `S` |

---

## Skills
- Spring WebSocket `afterConnectionClosed` lifecycle hook
- Token bucket / sliding window rate limiting algorithm
- `ConcurrentHashMap` safe removal patterns
- Java `ScheduledExecutorService` (for rate limiter window reset)

---

## Project Context

### Disconnect Flow (G09)
```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String sessionId = session.getId();
    StreamObserver<ClientMessage> stream = sessionManager.remove(sessionId);
    if (stream != null) {
        // Send LeaveRequest before closing
        stream.onNext(ClientMessage.newBuilder()
            .setLeaveRequest(LeaveRequest.newBuilder()
                .setPlayerId(sessionManager.getPlayerId(sessionId))
                .build())
            .build());
        stream.onCompleted(); // gracefully close gRPC stream
    }
    log.info("[ws] session {} disconnected: {}", sessionId, status);
}
```

### Rate Limiter Design (G10)
```java
// Sliding window: max 128 usercmd/sec per session
public class RateLimiter {
    private final int maxPerSecond;
    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public RateLimiter(int maxPerSecond) {
        this.maxPerSecond = maxPerSecond;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // Reset all counters every second
        scheduler.scheduleAtFixedRate(counters::clear, 1, 1, TimeUnit.SECONDS);
    }

    public boolean allow(String sessionId) {
        return counters.computeIfAbsent(sessionId, k -> new AtomicInteger())
                       .incrementAndGet() <= maxPerSecond;
    }
}
```

### Rate Limit Constant
```
MAX_USERCMD_PER_SECOND = 128   // 2× tick rate — allows for client frame spikes
```

### Warning Throttle
Log dropped messages at most **once per second per session** to avoid log flooding:
```java
// Track last-warned timestamp per session
if (shouldWarn(sessionId)) {
    log.warn("[ratelimit] session {} exceeding 128 usercmd/sec — dropping", sessionId);
}
```

---

## Responsibilities
- `afterConnectionClosed` is the **only** place session cleanup happens — no other code path removes sessions
- `LeaveRequest` must be sent before `onCompleted()` — order matters
- `RateLimiter` is a Spring `@Component` singleton — injected into `GameWebSocketHandler`
- Rate limiter counter resets cleanly on session removal (no stale counters)
- `RateLimiter.shutdown()` called in `@PreDestroy` to stop the scheduler

---

## Collaborates With
| Agent | Why |
|---|---|
| `gateway-grpc-bridge` | Inherits `SessionManager` — adds cleanup logic |
| `service-networking` | `LeaveRequest` triggers `RemovePlayer` in Go world |
| `qa-integration` | Integration test: rapid connect/disconnect cycle, no memory leak |

---

## Definition of Done Gate
- [ ] Close browser tab → Go service log shows player leaving within 1 tick
- [ ] 10 connect/disconnect cycles → `SessionManager` map size returns to 0 each time
- [ ] Session sending 500 usercmd/sec → only 128 forwarded, warning logged once/sec
- [ ] Normal sessions (< 128 usercmd/sec) are completely unaffected by rate limiter
- [ ] `mvn clean package` passes
