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

## Responsibilities
- [ ] Define all message types completely before gateway-grpc-bridge or service-networking starts
- [ ] Document every field with an inline comment in the `.proto` file
- [ ] Provide a JSON ↔ proto field mapping table for the frontend team
- [ ] Verify `protoc` generates clean Go stubs: `protoc --go_out=. --go-grpc_out=. proto/game.proto`
- [ ] Verify Maven generates clean Java stubs: `mvn generate-sources`
- [ ] Maintain a CHANGELOG comment block at the top of `game.proto` for any field additions

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
- [ ] `game.proto` compiles cleanly for both Go and Java
- [ ] All fields have inline comments
- [ ] A `proto-contract.md` field mapping table is committed to `.agents/` (this file)
- [ ] No field uses the reserved proto type `required` (proto3 only uses `optional` implicitly)
