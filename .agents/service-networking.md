# Agent: Service Networking
**ID:** `service-networking`
**Emoji:** 🟡
**Complexity:** Moderate

---

## Mission
Expose the Go game service over gRPC. Handle player joins, route incoming `usercmd` messages into per-player channels, and broadcast snapshots to all connected players every tick.

---

## Owns
- `service/net/grpc_handler.go`
- `service/proto/` *(generated `.pb.go` files)*
- Modifications to `service/main.go` (start gRPC listener)
- Modifications to `service/tick/loop.go` (add snapshot broadcast hook)

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| S07 | Start a gRPC server | `S` |
| S08 | Handle `JoinRequest` over gRPC stream | `S` |
| S09 | Route incoming `usercmd` messages to player channel | `S` |
| S10 | Build and broadcast a snapshot every tick | `M` |

---

## Skills
- Go gRPC server (`grpc.NewServer`, `pb.RegisterGameServiceServer`)
- Bidirectional streaming in Go (`GameService_PlayServer`)
- Go goroutines for concurrent stream reading
- Proto-generated Go types
- `protoc` + `protoc-gen-go` + `protoc-gen-go-grpc` codegen

---

## Project Context

### Proto Codegen Command
```bash
# Run from service/ directory
protoc --go_out=. --go-grpc_out=. \
       --go_opt=paths=source_relative \
       --go-grpc_opt=paths=source_relative \
       proto/game.proto
```
Or add to `go generate`:
```go
//go:generate protoc --go_out=. --go-grpc_out=. proto/game.proto
```

### gRPC Server Bootstrap (S07)
```go
func startGRPC(ctx context.Context, world *world.WorldState) {
    lis, _ := net.Listen("tcp", ":"+os.Getenv("GRPC_PORT"))
    s := grpc.NewServer()
    pb.RegisterGameServiceServer(s, &GameHandler{world: world})
    go func() {
        <-ctx.Done()
        s.GracefulStop()
    }()
    s.Serve(lis)
}
```

### Stream Handler Structure (S08-S09)
```go
func (h *GameHandler) Play(stream pb.GameService_PlayServer) error {
    // 1. Read first message — must be JoinRequest
    msg, _ := stream.Recv()
    join := msg.GetJoinRequest()
    player := h.world.AddPlayer(join.PlayerId)

    // Send JoinResponse
    stream.Send(&pb.ServerMessage{Payload: &pb.ServerMessage_JoinResponse{...}})

    // 2. Start snapshot sender goroutine
    snapCh := make(chan *pb.ServerMessage, 4)
    h.world.RegisterSnapshotCh(join.PlayerId, snapCh)

    go func() {
        for snap := range snapCh {
            stream.Send(snap)
        }
    }()

    // 3. Read usercmds in a loop
    for {
        msg, err := stream.Recv()
        if err != nil { break }
        if cmd := msg.GetUserCmd(); cmd != nil {
            h.world.Queues[join.PlayerId].Cmds <- convertCmd(cmd)
        }
        if leave := msg.GetLeaveRequest(); leave != nil { break }
    }

    h.world.RemovePlayer(join.PlayerId)
    close(snapCh)
    return nil
}
```

### Snapshot Build (S10)
```go
// Called at end of every tick
func buildSnapshot(w *WorldState, forPlayer string) *pb.Snapshot {
    snap := &pb.Snapshot{
        ServerTick: w.Tick,
        AckSeq:     w.LastAckSeq[forPlayer],
    }
    for id, p := range w.Players {
        snap.Entities = append(snap.Entities, &pb.EntityState{
            Id: id, X: float32(p.X), Y: float32(p.Y),
            Angle: float32(p.Angle), Health: int32(p.Health),
            IsSelf: id == forPlayer,
        })
    }
    return snap
}
```

---

## Responsibilities
- gRPC port defaults to `9090`, overridable via `GRPC_PORT` env var
- Each player gets a dedicated `snapCh` channel — snapshot sender goroutine drains it non-blocking
- If `snapCh` is full (slow client), drop the snapshot — never block the tick loop
- `LastAckSeq` per player is updated in S06 (service-world) and read here for snapshot
- All gRPC errors → remove player from world, close their snapshot channel

---

## Collaborates With
| Agent | Why |
|---|---|
| `proto-contract` | Consumes Go generated stubs |
| `service-world` | Consumes `WorldState`, `PlayerQueue`, `AddPlayer`, `RemovePlayer` |
| `gateway-grpc-bridge` | Integration: gateway sends join → service responds |
| `service-mechanics` | Hands off world state and tick hook for game mechanics |

---

## Definition of Done Gate
- [ ] `grpcurl -plaintext localhost:9090 list` shows `game.GameService`
- [ ] Browser join → Go log: `[join] player <id> spawned at (x, y)`
- [ ] `usercmd` from gateway arrives in player's channel (verified by movement)
- [ ] All connected players receive snapshots at ~64/sec (verified by browser overlay)
- [ ] Player disconnect → Go log: player removed, no goroutine leak
