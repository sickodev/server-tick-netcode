# Agent: Frontend Prediction
**ID:** `frontend-prediction`
**Emoji:** 🟡
**Complexity:** Moderate — requires deep coordination with `service-world`

---

## Mission
Implement the three Bernier latency-compensation techniques on the client: client-side prediction (your own movement feels instant), server reconciliation (corrections don't cause visible snapping), and entity interpolation (remote players move smoothly). This is the most technically complex frontend work.

---

## Owns
- `frontend/src/game/PredictionEngine.ts`
- `frontend/src/game/InterpolationBuffer.ts`
- Modifications to `GameEngine.ts` to wire prediction into the frame loop

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| F09 | Add client-side prediction for local player | `M` |
| F10 | Add server reconciliation | `M` |
| F11 | Add entity interpolation for remote players | `M` |

---

## Skills
- Fixed-timestep game simulation
- Ring buffer / circular buffer implementation in TypeScript
- Linear interpolation (`lerp`) math
- Understanding of Bernier 2001 paper — all three techniques
- **Critical:** must read `service/physics/movement.go` and replicate it exactly

---

## Project Context

### ⚠️ Critical Invariant
`PredictionEngine.applyMovement()` must produce **byte-for-byte identical results** to `physics.ApplyMovement()` in Go for the same inputs. If they diverge, reconciliation will fight itself every frame. Coordinate with `service-world` agent.

### Client-Side Prediction (F09)
```ts
// Instead of waiting for snapshot:
// 1. On input → simulate immediately
// 2. Store cmd in unACKed buffer
// 3. Move local player position locally

applyMovement(localPlayer, cmd, dt):
  localPlayer.x += cmd.dx * PLAYER_SPEED * dt
  localPlayer.y += cmd.dy * PLAYER_SPEED * dt
  localPlayer.x = clamp(localPlayer.x, PLAYER_RADIUS, ARENA_W - PLAYER_RADIUS)
  localPlayer.y = clamp(localPlayer.y, PLAYER_RADIUS, ARENA_H - PLAYER_RADIUS)
```

### Server Reconciliation (F10)
```ts
// On snapshot receive with ackSeq:
// 1. Discard commands ≤ ackSeq from buffer
// 2. Snap to server's position for ackSeq
// 3. Re-simulate remaining buffered commands
onSnapshot(snapshot):
  const serverPos = snapshot.entities.find(e => e.isSelf)
  discardBefore(snapshot.ackSeq)
  localPlayer.x = serverPos.x
  localPlayer.y = serverPos.y
  for (cmd of unackedBuffer):
    applyMovement(localPlayer, cmd, TICK_DURATION / 1000)
```

### Entity Interpolation (F11)
```ts
// Remote entity render time = now - interpDelay
const interpDelay = 2 * TICK_DURATION // ~31ms

// Find two snapshots bracketing renderTime
const renderTime = Date.now() - interpDelay
const [snapA, snapB] = findBracketing(renderTime)
const t = (renderTime - snapA.time) / (snapB.time - snapA.time)
const pos = lerp(snapA.pos, snapB.pos, t)
```

---

## Responsibilities
- `PredictionEngine` owns: unACKed buffer, local movement simulation, reconciliation logic
- `InterpolationBuffer` owns: snapshot history (last 8), `getInterpolated(entityId, renderTime)` method
- `GameEngine` update sequence after these stories:
  1. `InputHandler.flush()` → `UserCmd`
  2. `PredictionEngine.simulate(cmd)` → update `localPlayer`
  3. `NetworkClient.send(cmd)` → push to server
  4. `InterpolationBuffer.tick()` → get remote entity positions
  5. `Renderer.draw(localPlayer, remoteEntities, bullets)`

---

## Collaborates With
| Agent | Why |
|---|---|
| `service-world` | **Must coordinate on movement constants** — applyMovement must be identical |
| `frontend-networking` | Inherits `UserCmd` type, `GameEngine` structure, snapshot parsing |
| `frontend-polish` | Hands off prediction error value for debug overlay |
| `qa-integration` | Integration test: add 200ms artificial latency, verify no visible jitter |

---

## Definition of Done Gate
- [ ] With Chrome DevTools → Network → throttle to "Slow 3G" (300ms RTT): local player still moves instantly
- [ ] Prediction error (server vs predicted position) is < 1px under normal conditions
- [ ] Remote players move smoothly with no teleporting under normal packet delivery
- [ ] Unit test: `PredictionEngine.applyMovement` matches Go `physics.ApplyMovement` for 1000 random inputs
- [ ] Buffer does not grow unboundedly (old commands are discarded)
