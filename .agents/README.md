# Agent Roster — server-tick-netcode

> [!IMPORTANT]
> **All agents must read [AGENTS.md](../AGENTS.md) before starting any work.**
> It defines commit format, branch strategy, the code review gate, the physics invariant, and all team rules.

This folder contains all project agents. Each agent owns a specific slice of the codebase and story set. Agents are designed to work independently on their stories and hand off clearly defined outputs to dependent agents.

## Agent Map

```
.agents/
│
├── README.md                        ← this file
├── code-reviewer.md                 ← 🔍 Pre-push review gate (all agents)
│
├── proto-contract.md                ← 🔵 Shared contract owner (start here)
│
├── Frontend
│   ├── frontend-gameloop.md         ← 🟢 F01–F04  Canvas + local movement
│   ├── frontend-networking.md       ← 🟢 F05–F08  WebSocket + snapshot rendering
│   ├── frontend-prediction.md       ← 🟡 F09–F11  Prediction + interpolation
│   └── frontend-polish.md           ← 🟢 F12–F13  Shooting + debug overlay
│
├── Gateway
│   ├── gateway-websocket.md         ← 🟢 G01–G03  WebSocket foundation
│   ├── gateway-grpc-bridge.md       ← 🟡 G04–G08  gRPC bridge
│   └── gateway-resilience.md        ← 🟢 G09–G10  Disconnect + rate limiting
│
├── Service
│   ├── service-world.md             ← 🟢 S01–S06  Tick loop + world state
│   ├── service-networking.md        ← 🟡 S07–S10  gRPC server + snapshots
│   └── service-mechanics.md         ← 🔴 S11–S14  Lag compensation + bullets
│
├── Cross-cutting
│   ├── ux-design.md                 ← 🎨 Visual design + game feel
│   ├── qa-integration.md            ← 🧪 End-to-end testing + integration
│   └── devops-deploy.md             ← 🚀 Docker + deployments (Vercel/Koyeb/Fly.io)
```

## Complexity Legend
- 🟢 Straightforward — well-defined scope, low ambiguity
- 🟡 Moderate — requires cross-agent coordination or careful design
- 🔴 Hard — deep algorithmic work, must coordinate with frontend-prediction agent
- 🔵 Foundational — all other agents depend on this

## Dependency Order

```
proto-contract
    ├── gateway-grpc-bridge
    └── service-networking
            └── service-mechanics
                    └── qa-integration

frontend-gameloop
    └── frontend-networking
            └── frontend-prediction
                    └── frontend-polish

gateway-websocket
    └── gateway-grpc-bridge
            └── gateway-resilience

service-world
    └── service-networking

ux-design  (parallel, informs frontend agents)
devops-deploy  (parallel, acts last)
```

## Shared Project Constants

All agents must use these values consistently. They are the source of truth.

| Constant | Value | Defined in |
|---|---|---|
| `ARENA_W` | `1200` px | `service/physics/constants.go` + `frontend/src/game/constants.ts` |
| `ARENA_H` | `800` px | same |
| `PLAYER_SPEED` | `200` px/sec | same |
| `PLAYER_RADIUS` | `20` px | same |
| `BULLET_SPEED` | `600` px/sec | same |
| `BULLET_DAMAGE` | `25` hp | service only |
| `TICK_RATE` | `64` Hz | service + frontend |
| `TICK_DURATION` | `15.625` ms | derived |
| `INTERP_TICKS` | `2` | frontend |
| `HISTORY_SIZE` | `128` ticks | service |
