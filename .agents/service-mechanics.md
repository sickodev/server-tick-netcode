# Agent: Service Mechanics
**ID:** `service-mechanics`
**Emoji:** 🔴
**Complexity:** Hard — algorithmic work, coordinate with `frontend-prediction`

---

## Mission
Implement the game's authoritative mechanics: a lag-compensation history buffer, bullet physics, ray-vs-circle hit detection with time rewind, and damage/health. This is where Bernier's server-side lag compensation lives.

---

## Owns
- `service/world/history.go`
- `service/physics/hitdetect.go` *(create)*
- Modifications to `service/tick/loop.go` (bullet advance, history record, hit detection)
- Modifications to `service/world/state.go` (BulletState lifecycle, health)

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| S11 | Implement the lag-compensation history buffer | `M` |
| S12 | Spawn and advance bullets | `S` |
| S13 | Implement lag-compensated hit detection | `M` |
| S14 | Apply damage and broadcast hit events | `S` |

---

## Skills
- Ring buffer (circular array) implementation in Go
- Deep copy of complex structs in Go (avoid pointer aliasing in history)
- Ray vs circle intersection math
- Concurrency-safe history reads (multiple goroutines may query simultaneously)
- Time-domain rewinding logic

---

## Project Context

### History Buffer (S11)
```go
// Circular ring buffer — no dynamic allocation per tick
type HistoryBuffer struct {
    states  [physics.HistorySize]world.WorldState
    head    int
    mu      sync.RWMutex
}

func (h *HistoryBuffer) Record(state world.WorldState) {
    h.mu.Lock()
    defer h.mu.Unlock()
    h.states[h.head % physics.HistorySize] = deepCopy(state)
    h.head++
}

func (h *HistoryBuffer) StateAt(tick int64) *world.WorldState {
    h.mu.RLock()
    defer h.mu.RUnlock()
    idx := int(tick) % physics.HistorySize
    if h.states[idx].Tick != tick { return nil } // overwritten
    s := h.states[idx]
    return &s
}
```

### Bullet Spawn (S12)
```go
func spawnBullet(owner *world.PlayerState, ownerID string, tick int64) *world.BulletState {
    return &world.BulletState{
        ID:       uuid.New().String(),
        OwnerID:  ownerID,
        X:        owner.X + math.Cos(owner.Angle) * physics.PlayerRadius,
        Y:        owner.Y + math.Sin(owner.Angle) * physics.PlayerRadius,
        VX:       math.Cos(owner.Angle) * physics.BulletSpeed,
        VY:       math.Sin(owner.Angle) * physics.BulletSpeed,
        BornTick: tick,
    }
}
```

### Lag Compensation Rewind (S13)
```go
func lagCompensatedHit(
    shooter *world.PlayerState, shooterLatencyTicks int64,
    currentTick int64, history *world.HistoryBuffer,
) string { // returns hit playerID or ""

    rewindTick := currentTick - shooterLatencyTicks - physics.InterpTicks
    past := history.StateAt(rewindTick)
    if past == nil { return "" }

    origin := [2]float64{shooter.X, shooter.Y}
    dir    := [2]float64{math.Cos(shooter.Angle), math.Sin(shooter.Angle)}

    for id, p := range past.Players {
        if id == shooter ID { continue }
        if physics.RayVsCircle(origin, dir, [2]float64{p.X, p.Y}, physics.PlayerRadius) {
            return id
        }
    }
    return ""
}
```

### Ray vs Circle (S13)
```go
// Returns true if ray from `origin` in direction `dir` intersects circle at `center` radius `r`
func RayVsCircle(origin, dir, center [2]float64, r float64) bool {
    // Vector from origin to circle center
    oc := [2]float64{center[0] - origin[0], center[1] - origin[1]}
    // Project oc onto dir
    t := oc[0]*dir[0] + oc[1]*dir[1]
    if t < 0 { return false } // circle is behind the ray
    // Closest point on ray to circle center
    closest := [2]float64{origin[0] + dir[0]*t, origin[1] + dir[1]*t}
    dist := math.Hypot(closest[0]-center[0], closest[1]-center[1])
    return dist <= r
}
```

### Respawn (S14)
```go
// After elimination, respawn after 3 seconds in a goroutine
go func() {
    time.Sleep(3 * time.Second)
    world.AddPlayer(eliminatedID) // re-add with full health at new spawn
}()
```

---

## Responsibilities
- History buffer records world state **before** movement is applied each tick
- Deep copy in `Record()` is mandatory — pointer aliasing breaks history correctness
- Rewind tick must be clamped to available history range (don't query older than `HistorySize` ticks)
- `go test ./physics/...` must include: direct hit, near miss, behind cover (all three ray cases)
- Fire cooldown (1 shot per 10 ticks) is enforced here, not in the gateway

---

## Collaborates With
| Agent | Why |
|---|---|
| `service-networking` | Tick loop already exists — this agent adds history record + hit detection calls |
| `frontend-prediction` | **The "behind cover" behavior must be explained to frontend agent** — victim may appear safe locally but be hit |
| `qa-integration` | Lag compensation correctness test: simulate 50ms latency, fire, verify hit |

---

## Definition of Done Gate
- [ ] `go test ./world/...` — history buffer: 200 records, StateAt works for last 128, nil for older
- [ ] `go test ./physics/...` — RayVsCircle: direct hit ✓, near miss ✓, behind-ray ✓
- [ ] Fire with a second browser tab visible → hits register correctly (verified by health decrease)
- [ ] Fire cooldown enforced: rapid click only fires once per 10 ticks
- [ ] Health reaches 0 → player removed, respawns after 3 seconds
- [ ] No goroutine leak from respawn timers (use context cancellation)
