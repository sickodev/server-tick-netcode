# Agent: Service World
**ID:** `service-world`
**Emoji:** 🟢
**Complexity:** Straightforward

---

## Mission
Build the Go game service foundation: a precise 64Hz tick loop, all core world state types, deterministic movement physics, and player lifecycle (add/remove). This is the engine that everything else runs on.

---

## Owns
- `service/main.go`
- `service/tick/loop.go`
- `service/world/state.go`
- `service/physics/movement.go`
- `service/physics/constants.go` *(create)*

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| S01 | App starts and prints a tick counter | `XS` |
| S02 | Define core world state structs | `XS` |
| S03 | Move the tick loop into its own package | `S` |
| S04 | Implement deterministic player movement | `S` |
| S05 | Add players to the world on join | `S` |
| S06 | Apply movement commands in the tick loop | `S` |

---

## Skills
- Go goroutines, channels, `context.Context` cancellation
- `time.NewTicker` precision at 64Hz
- `sync.RWMutex` for concurrent world state access
- Deterministic float64 arithmetic (same result every time for same inputs)
- Go `testing` package, table-driven tests

---

## Project Context

### ⚠️ Critical Invariant
`physics.ApplyMovement` is the **single source of truth** for movement. The frontend `PredictionEngine.ts` must replicate it exactly. Share this with the `frontend-prediction` agent immediately after S04 is complete.

### Constants (`service/physics/constants.go`)
```go
package physics

const (
    ArenaW       = 1200.0  // px
    ArenaH       = 800.0   // px
    PlayerRadius = 20.0    // px
    PlayerSpeed  = 200.0   // px/sec
    BulletSpeed  = 600.0   // px/sec
    BulletDamage = 25      // hp
    TickRate     = 64      // Hz
    TickDuration = 1.0 / TickRate // seconds per tick
    HistorySize  = 128     // ticks (~2 seconds)
)
```

### Tick Loop (S03)
```go
type Loop struct {
    OnTick func(tick int64)
}

func (l *Loop) Start(ctx context.Context) {
    ticker := time.NewTicker(time.Second / physics.TickRate)
    defer ticker.Stop()
    var tick int64
    for {
        select {
        case <-ticker.C:
            tick++
            l.OnTick(tick)
        case <-ctx.Done():
            return
        }
    }
}
```

### ApplyMovement (S04)
```go
func ApplyMovement(p *world.PlayerState, dx, dy, aimAngle, dt float64) {
    p.X += dx * PlayerSpeed * dt
    p.Y += dy * PlayerSpeed * dt
    p.Angle = aimAngle
    p.X = math.Max(PlayerRadius, math.Min(ArenaW-PlayerRadius, p.X))
    p.Y = math.Max(PlayerRadius, math.Min(ArenaH-PlayerRadius, p.Y))
}
```

### Per-Player Command Queue (S06)
```go
// Buffered channel — tick loop drains non-blocking each tick
type PlayerQueue struct {
    Cmds chan UserCmd // capacity 128
}

// In tick loop:
for id, q := range world.Queues {
    loop:
    for {
        select {
        case cmd := <-q.Cmds:
            physics.ApplyMovement(world.Players[id], cmd.DX, cmd.DY, cmd.AimAngle, physics.TickDuration)
            world.LastAckSeq[id] = cmd.Seq
        default:
            break loop
        }
    }
}
```

---

## Responsibilities
- `WorldState` has a `sync.RWMutex` — all reads use `RLock`, all writes use `Lock`
- Spawn positions are random within inner 80% of arena (not on edge) — use `math/rand`
- `RemovePlayer` also removes all bullets owned by that player
- `go test ./...` must pass at every story completion

---

## Collaborates With
| Agent | Why |
|---|---|
| `frontend-prediction` | **Must share `ApplyMovement` implementation immediately after S04** |
| `service-networking` | Hands off `Loop`, `WorldState`, `PlayerQueue` for them to consume |
| `qa-integration` | Determinism unit test: 1000 random inputs, same result every time |

---

## Definition of Done Gate
- [ ] `go run main.go` prints `tick: 64` once per second (±2)
- [ ] `go test ./physics/...` passes — same inputs → same position every run
- [ ] `go test ./tick/...` passes — 64 ticks in 1 second ±5ms
- [ ] `go test ./world/...` passes — add/remove/concurrent access
- [ ] `go vet ./...` and `go build ./...` pass with zero warnings
