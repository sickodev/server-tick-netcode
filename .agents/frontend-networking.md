# Agent: Frontend Networking
**ID:** `frontend-networking`
**Emoji:** 🟢
**Complexity:** Straightforward

---

## Mission
Connect the game to the gateway over WebSocket: send a join message, stream `usercmd` packets every frame, and receive server snapshots. Render server-authoritative positions directly (no prediction yet). This is the first time two browsers can see each other.

---

## Owns
- `frontend/src/game/NetworkClient.ts`
- `frontend/src/game/types.ts` *(create — shared type definitions)*
- `frontend/.env.local` *(document only, never commit)*
- `frontend/.env.example` *(commit this as a template)*

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| F05 | Open a WebSocket connection to the gateway | `S` |
| F06 | Send a join message on connect | `S` |
| F07 | Send `usercmd` messages every frame | `S` |
| F08 | Receive snapshots and render server positions | `S` |

---

## Skills
- Browser `WebSocket` API (readyState checks, `onmessage`, `onclose`, `onerror`)
- JSON serialisation/deserialisation
- `crypto.randomUUID()` for session identity
- Event-driven architecture (callbacks / event emitter pattern)
- `import.meta.env` for Vite environment variables

---

## Project Context

### Message Shapes (JSON over WebSocket)

**Sent by client:**
```ts
// On connect
{ type: "join", playerId: string, name: string }

// Every frame
{ type: "usercmd", seq: number, timestamp: number,
  dx: number, dy: number, aimAngle: number, fire: boolean }
```

**Received from gateway:**
```ts
// After join
{ type: "joinResponse", ok: boolean, spawnX: number, spawnY: number }

// Every server tick (~64/sec)
{
  type: "snapshot",
  serverTick: number,
  ackSeq: number,
  entities: Array<{ id: string, x: number, y: number, angle: number, health: number, isSelf: boolean }>,
  bullets:  Array<{ id: string, ownerId: string, x: number, y: number, vx: number, vy: number }>
}
```

### Environment Variable
```
# frontend/.env.local  (never commit)
VITE_GATEWAY_URL=ws://localhost:8080/ws
```

### Reconnect Strategy
```ts
// Reconnect with exponential backoff capped at 5s
let delay = 1000;
socket.onclose = () => {
  setTimeout(() => connect(), Math.min(delay *= 2, 5000));
};
```

### `UserCmd.seq` Counter
- Starts at `1` when the page loads
- Increments by `1` every frame regardless of whether input changed
- Never resets (even after reconnect in this MVP)

---

## Responsibilities
- `NetworkClient` owns the WebSocket lifecycle (open, send, receive, reconnect)
- On snapshot receive: update `GameEngine` with entity positions and bullet positions
- For now, snap local player **directly** to `isSelf` entity position from snapshot (prediction comes in F09)
- Remote players drawn as grey circles (`#888888`)
- Bullets drawn as small white dots (`radius = 5`)
- Define `UserCmd`, `Snapshot`, `EntityState`, `BulletState` as TypeScript interfaces in `types.ts`

---

## Collaborates With
| Agent | Why |
|---|---|
| `frontend-gameloop` | Inherits working canvas loop — extends it with network data |
| `frontend-prediction` | Hands off `UserCmd` type and unACKed buffer slot in `GameEngine` |
| `gateway-websocket` | Must align on JSON message shapes |

---

## Definition of Done Gate
- [ ] Two browser tabs both show circles and see each other move
- [ ] Network tab in DevTools shows outbound `usercmd` frames at ~60/sec
- [ ] Inbound `snapshot` frames arriving at ~64/sec
- [ ] Closing one tab removes that player from the other tab within 1 tick
- [ ] `npm run build` passes with no TypeScript errors
