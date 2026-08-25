# Service Stories — Go Game Server

Stories are ordered strictly by dependency. Each one should be completable and committable on its own before the next begins.

**Complexity scale:** `XS` < 1 hr · `S` 1–2 hr · `M` 2–4 hr · `L` 4–8 hr

---

## ⏱️ Tick Loop Foundation

---

### STORY-S01 · App starts and prints a tick counter
**Complexity:** `XS`

**Description**
The simplest possible Go program: start a `time.NewTicker` at 64Hz (every `time.Second / 64`) and print the tick count to stdout once per second. Confirms the ticker works and the module builds.

**Definition of Done**
- [x] `go run main.go` starts without errors
- [x] Console prints `tick: 64`, `tick: 128`, … once per second (±2 ticks tolerance)
- [x] Program exits cleanly on `Ctrl+C` (signal handling via `os.Signal`)
- [x] Ticker goroutine does not leak on shutdown

---

### STORY-S02 · Define core world state structs
**Complexity:** `XS`

**Description**
In `world/state.go`, define `PlayerState`, `BulletState`, and `WorldState`. No logic yet — just the data types the rest of the service will build on.

**Definition of Done**
- [x] `PlayerState` has: `ID string`, `X, Y float64`, `Angle float64`, `Health int`, `Speed float64`
- [x] `BulletState` has: `ID string`, `OwnerID string`, `X, Y float64`, `VX, VY float64`, `BornTick int64`
- [x] `WorldState` has: `Tick int64`, `Players map[string]*PlayerState`, `Bullets []*BulletState`
- [x] `go build ./...` passes with no errors
- [x] All fields are exported (capital first letter)

---

### STORY-S03 · Move the tick loop into its own package
**Complexity:** `S`

**Description**
Extract the ticker from `main.go` into `tick/loop.go`. Define a `Loop` struct with a `Start(ctx context.Context)` method that runs the 64Hz tick and calls a pluggable `onTick func(tick int64)` callback. `main.go` wires it up.

**Definition of Done**
- [x] `tick.Loop` is instantiated in `main.go` with an `onTick` callback
- [x] `onTick` receives the current tick number on each call
- [x] Loop shuts down cleanly when the `context` is cancelled
- [x] `go test ./tick/...` passes (test that 64 ticks fire within 1 second ± 5ms)
- [x] No goroutine leak on shutdown (verified with `goleak` or manual check)

---

## 🌍 World Simulation

---

### STORY-S04 · Implement deterministic player movement
**Complexity:** `S`

**Description**
In `physics/movement.go`, implement `ApplyMovement(p *PlayerState, dx, dy, aimAngle float64, dt float64)`. This is the **single source of truth** for movement — the frontend `PredictionEngine` must replicate this exactly.

**Definition of Done**
- [x] `p.X` and `p.Y` are updated by `dx * Speed * dt` and `dy * Speed * dt`
- [x] `p.Angle` is set to `aimAngle`
- [x] Player cannot leave the arena (clamp to `[0, ARENA_W]` × `[0, ARENA_H]`)
- [x] `ARENA_W`, `ARENA_H`, `DEFAULT_SPEED` are named constants in a `physics/constants.go` file
- [x] `go test ./physics/...` passes: same input always produces same output (determinism test)

---

### STORY-S05 · Add players to the world on join
**Complexity:** `S`

**Description**
In `world/state.go`, add `AddPlayer(id string) *PlayerState` which inserts a new player at a random spawn point within the arena with full health. Add `RemovePlayer(id string)`.

**Definition of Done**
- [x] `AddPlayer` inserts a `PlayerState` into `WorldState.Players` with `Health = 100`
- [x] Spawn position is random within the inner 80% of the arena (not on the edge)
- [x] `RemovePlayer` deletes the player and all their bullets
- [x] Both are safe to call concurrently (protected by a `sync.RWMutex` on `WorldState`)
- [x] Unit tests cover: add, remove, duplicate add (should overwrite)

---

### STORY-S06 · Apply movement commands in the tick loop
**Complexity:** `S`

**Description**
Each tick, drain the per-player `usercmd` channel (non-blocking) and call `physics.ApplyMovement` for each received command. Use a `map[string]chan UserCmd` to hold the per-player command queues.

**Definition of Done**
- [x] Each player has a buffered `chan UserCmd` (capacity 128)
- [x] Tick loop drains all pending commands for all players using a non-blocking `select`
- [x] Only the **latest** command per player per tick is applied (excess are discarded if channel overflows)
- [x] Players with no pending commands do not move
- [x] `go test ./tick/...` includes a test: enqueue a move command, run one tick, assert new position

---

## 📡 Networking

---

### STORY-S07 · Start a gRPC server
**Complexity:** `S`

**Description**
In `net/grpc_handler.go`, implement the `GameService` gRPC server interface generated from `game.proto`. Start the gRPC listener in `main.go` on port `9090`.

**Definition of Done**
- [x] `go run main.go` starts a gRPC server on `:9090`
- [x] `grpcurl -plaintext localhost:9090 list` shows `game.GameService`
- [x] Server shuts down gracefully on `Ctrl+C`
- [x] Proto-generated code is committed (or generated via `go generate`)
- [x] Port is configurable via an environment variable `GRPC_PORT` (default `9090`)

---

### STORY-S08 · Handle `JoinRequest` over gRPC stream
**Complexity:** `S`

**Description**
In the `Play` stream handler, read the first `ClientMessage`. If it is a `JoinRequest`, call `world.AddPlayer`, send back a `JoinResponse` with spawn position, and log the join event.

**Definition of Done**
- [x] First message on any `Play` stream must be a `JoinRequest` (other types are rejected with an error response)
- [x] `JoinResponse` contains `ok: true`, `spawn_x`, `spawn_y`
- [x] Server logs `[join] player <id> spawned at (x, y)`
- [x] If `player_id` is empty, respond with `ok: false, error: "player_id required"`
- [x] Tested end-to-end: Java gateway sends join, Go logs it

---

### STORY-S09 · Route incoming `usercmd` messages to the player's channel
**Complexity:** `S`

**Description**
After the `JoinRequest` is handled, read subsequent `ClientMessage`s in the stream. For each `UserCmd`, push it onto the player's command channel (non-blocking drop on full).

**Definition of Done**
- [x] `UserCmd` proto fields are converted to an internal `UserCmd` struct before enqueue
- [x] Channel send is non-blocking: if the channel is full, the command is dropped and a counter is incremented
- [x] A dropped-command counter is logged once per second if non-zero
- [x] `LeaveRequest` on the stream triggers player removal and stream close
- [x] Stream errors (client disconnect) also trigger player removal

---

### STORY-S10 · Build and broadcast a snapshot every tick
**Complexity:** `M`

**Description**
At the end of each tick, construct a `Snapshot` proto for each connected player (setting `is_self = true` for their own entity) and send it on their gRPC stream via a per-player `chan *pb.ServerMessage` (non-blocking).

**Definition of Done**
- [x] Each connected player receives a `Snapshot` every tick (~64/sec)
- [x] `ack_seq` in the snapshot is the `seq` of the last `UserCmd` processed for that player
- [x] `is_self` is `true` for exactly one `EntityState` per snapshot (the receiving player)
- [x] Snapshot send is non-blocking (slow clients are dropped, not blocked)
- [x] `server_tick` increments by 1 on every snapshot

---

## 🏆 Game Mechanics

---

### STORY-S11 · Implement the lag-compensation history buffer
**Complexity:** `M`

**Description**
In `world/history.go`, implement a circular ring buffer that stores a deep copy of `WorldState` every tick (128 entries = 2 seconds at 64Hz). Expose `StateAt(tick int64) *WorldState`.

**Definition of Done**
- [x] Buffer stores exactly 128 entries in a fixed-size array (no dynamic allocation per tick)
- [x] `StateAt` returns the state for a given tick, or `nil` if that tick has been overwritten
- [x] `Record(state WorldState)` stores a **deep copy** (mutations to live state do not affect history)
- [x] Buffer wraps correctly: entry 129 overwrites entry 1
- [x] `go test ./world/...` includes: record 200 states, assert StateAt for ticks 73–200 returns correct data, ticks 1–72 return nil

---

### STORY-S12 · Spawn and advance bullets
**Complexity:** `S`

**Description**
When a `UserCmd` has `fire: true`, spawn a `BulletState` at the player's position moving in the `aimAngle` direction at `BULLET_SPEED`. Each tick, advance all bullets by their velocity. Remove bullets that leave the arena.

**Definition of Done**
- [x] Firing spawns a bullet with `VX = cos(aimAngle) * BULLET_SPEED`, `VY = sin(aimAngle) * BULLET_SPEED`
- [x] `BULLET_SPEED` is a named constant (e.g. `600` px/sec)
- [x] Bullets are advanced each tick: `X += VX * dt`, `Y += VY * dt`
- [x] Bullets outside the arena bounds are removed from `WorldState.Bullets`
- [x] Bullets appear in the `Snapshot.bullets` field sent to clients
- [x] A player cannot fire faster than once every 16 ticks (cooldown enforced server-side)

---

### STORY-S13 · Implement lag-compensated hit detection
**Complexity:** `M`

**Description**
When a `fire: true` command is processed, calculate the rewind tick (`currentTick - latencyTicks - interpTicks`), retrieve the historical world state, and perform a ray-vs-circle intersection test from the player's weapon position along `aimAngle` against all other players' hitboxes at that rewound state.

**Definition of Done**
- [x] Rewind tick = `currentTick - clientLatencyTicks - 2` (2 ticks for interp delay)
- [x] `clientLatencyTicks` is estimated as `(serverReceiveTime - cmd.Timestamp) / tickDuration`
- [x] `physics.RayVsCircle(origin, direction, center, radius float64) bool` implemented in `physics/hitdetect.go`
- [x] Hit detection runs against the **historical** world state, not the current state
- [x] World state is restored after hit detection (history buffer is read-only)
- [x] `go test ./physics/...` covers: direct hit, near miss, shot behind cover

---

### STORY-S14 · Apply damage and broadcast hit events
**Complexity:** `S`

**Description**
When a hit is confirmed, reduce the target's `Health` by `BULLET_DAMAGE` (default 25). If health reaches 0, remove the player and include a `hit` flag in the next snapshot. Respawn the eliminated player after 3 seconds.

**Definition of Done**
- [x] `BULLET_DAMAGE = 25` (4 shots to eliminate)
- [x] `Health` floor is 0 (no negative health)
- [x] Eliminated players are removed from the world immediately
- [x] Eliminated player reconnects with full health after a 3-second timer goroutine
- [x] The snapshot's `EntityState.health` field reflects current health for all players
- [x] Server log: `[hit] <shooter> → <target> (health: <remaining>)`

---

## Summary

| Story | Title | Complexity |
|---|---|---|
| S01 | App starts and prints a tick counter | `XS` |
| S02 | Define core world state structs | `XS` |
| S03 | Move the tick loop into its own package | `S` |
| S04 | Implement deterministic player movement | `S` |
| S05 | Add players to the world on join | `S` |
| S06 | Apply movement commands in the tick loop | `S` |
| S07 | Start a gRPC server | `S` |
| S08 | Handle `JoinRequest` over gRPC stream | `S` |
| S09 | Route incoming `usercmd` messages to player channel | `S` |
| S10 | Build and broadcast a snapshot every tick | `M` |
| S11 | Implement the lag-compensation history buffer | `M` |
| S12 | Spawn and advance bullets | `S` |
| S13 | Implement lag-compensated hit detection | `M` |
| S14 | Apply damage and broadcast hit events | `S` |
