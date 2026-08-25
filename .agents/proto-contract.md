# Agent: Proto Contract Owner
**ID:** `proto-contract`
**Emoji:** 🔵
**Complexity:** Foundational — all networking agents depend on this

---

## Mission
Own and maintain `game.proto` — the single source of truth for all messages exchanged between the Java gateway and Go game service. No other agent modifies the proto file without this agent's review.

---

## Owns
- `service/proto/game.proto`
- Any future `.proto` files added to the project
- Proto versioning and backward-compatibility decisions

---

## Stories
This agent is prerequisite work before:
- `gateway-grpc-bridge` can begin G04
- `service-networking` can begin S07

---

## Skills
- Protobuf 3 schema design
- gRPC service definition (unary, bidirectional streaming)
- JSON ↔ Proto field name mapping conventions (camelCase in JSON, snake_case in proto)
- `protoc` codegen for Go (`protoc-gen-go`, `protoc-gen-go-grpc`)
- `protobuf-maven-plugin` codegen for Java

---

## Project Context

### Current Proto Location
`service/proto/game.proto`

### Message Contract

```
Browser (JSON) ──────────────── Gateway (Java) ──────── Go Service
{ type:"join" }            →    JoinRequest proto    →   Play stream
{ type:"usercmd", seq:N }  →    UserCmd proto        →   Play stream
                           ←    Snapshot proto        ←   Play stream
```

### Key Design Rules
1. `player_id` is always set by the **gateway** from the session — never trusted from the browser
2. `ack_seq` in Snapshot is the last `UserCmd.seq` processed for **that specific player**
3. `is_self` is computed per-player by the gateway (or service) before sending each snapshot
4. Proto field numbers must never be reused once deployed (backward compat)

---

## JSON ↔ Protobuf Field Mapping Reference

| Proto Message | Proto Field (`snake_case`) | Proto Type | JSON Key (`camelCase`) | JSON Type | Description / Notes |
|---|---|---|---|---|---|
| `JoinRequest` | `player_id` | `string` | `playerId` | `string` | Player ID (injected by gateway from session) |
| `JoinRequest` | `name` | `string` | `name` | `string` | Player chosen display name |
| `JoinResponse` | `ok` | `bool` | `ok` | `boolean` | Success flag for join admission |
| `JoinResponse` | `spawn_x` | `float` | `spawnX` | `number` | Initial spawn X coordinate |
| `JoinResponse` | `spawn_y` | `float` | `spawnY` | `number` | Initial spawn Y coordinate |
| `UserCmd` | `player_id` | `string` | `playerId` | `string` | Player ID (injected by gateway) |
| `UserCmd` | `seq` | `int32` | `seq` | `number` | Client command sequence number |
| `UserCmd` | `timestamp` | `int64` | `timestamp` | `number` | Input timestamp in milliseconds |
| `UserCmd` | `dx` | `float` | `dx` | `number` | Movement vector X (-1.0 to 1.0) |
| `UserCmd` | `dy` | `float` | `dy` | `number` | Movement vector Y (-1.0 to 1.0) |
| `UserCmd` | `aim_angle` | `float` | `aimAngle` | `number` | Cursor angle in radians |
| `UserCmd` | `fire` | `bool` | `fire` | `boolean` | Weapon fire trigger flag |
| `EntityState` | `id` | `string` | `id` | `string` | Entity / player ID |
| `EntityState` | `x` | `float` | `x` | `number` | X position in arena pixels |
| `EntityState` | `y` | `float` | `y` | `number` | Y position in arena pixels |
| `EntityState` | `angle` | `float` | `angle` | `number` | Aim / facing direction in radians |
| `EntityState` | `health` | `int32` | `health` | `number` | Current health points (0 = eliminated) |
| `EntityState` | `is_self` | `bool` | `isSelf` | `boolean` | True if this entity is the local player |
| `BulletState` | `id` | `string` | `id` | `string` | Bullet unique identifier |
| `BulletState` | `owner_id` | `string` | `ownerId` | `string` | Player ID of shooter |
| `BulletState` | `x` | `float` | `x` | `number` | Bullet X position in arena pixels |
| `BulletState` | `y` | `float` | `y` | `number` | Bullet Y position in arena pixels |
| `BulletState` | `vx` | `float` | `vx` | `number` | Bullet X velocity (pixels/sec) |
| `BulletState` | `vy` | `float` | `vy` | `number` | Bullet Y velocity (pixels/sec) |
| `Snapshot` | `server_tick` | `int64` | `serverTick` | `number` | Authoritative server tick number |
| `Snapshot` | `ack_seq` | `int32` | `ackSeq` | `number` | Highest acked UserCmd seq for receiver |
| `Snapshot` | `entities` | `repeated EntityState` | `entities` | `Array<Entity>` | Active entities in world |
| `Snapshot` | `bullets` | `repeated BulletState` | `bullets` | `Array<Bullet>` | Active bullets in flight |
| `LeaveRequest` | `player_id` | `string` | `playerId` | `string` | Player ID departing the match |
| `ClientMessage` | `join_request` | `JoinRequest` | `joinRequest` | `object` | Envelope payload for join |
| `ClientMessage` | `user_cmd` | `UserCmd` | `userCmd` | `object` | Envelope payload for input cmd |
| `ClientMessage` | `leave_request` | `LeaveRequest` | `leaveRequest` | `object` | Envelope payload for leave |
| `ServerMessage` | `join_response` | `JoinResponse` | `joinResponse` | `object` | Envelope payload for join ack |
| `ServerMessage` | `snapshot` | `Snapshot` | `snapshot` | `object` | Envelope payload for snapshot |

---

## Responsibilities
- [x] Define all message types completely before gateway-grpc-bridge or service-networking starts
- [x] Document every field with an inline comment in the `.proto` file
- [x] Provide a JSON ↔ proto field mapping table for the frontend team
- [x] Verify `protoc` generates clean Go stubs: `protoc --go_out=. --go-grpc_out=. proto/game.proto`
- [x] Verify Maven generates clean Java stubs: `mvn generate-sources`
- [x] Maintain a CHANGELOG comment block at the top of `game.proto` for any field additions

---

## Collaborates With
| Agent | Why |
|---|---|
| `gateway-grpc-bridge` | Consumes generated Java stubs |
| `service-networking` | Consumes generated Go stubs |
| `qa-integration` | Validates message shapes in integration tests |

---

## Definition of Done Gate
Before handing off to dependent agents:
- [x] `game.proto` compiles cleanly for both Go and Java
- [x] All fields have inline comments
- [x] A `proto-contract.md` field mapping table is committed to `.agents/` (this file)
- [x] No field uses the reserved proto type `required` (proto3 only uses `optional` implicitly)

