# server-tick-netcode

Networked 2D top-down shooter implementing Bernier's three latency compensation techniques.

## Structure

| Folder | Stack | Purpose |
|---|---|---|
| `frontend/` | React + Vite + TypeScript | Client-side prediction, entity interpolation, canvas renderer |
| `gateway/` | Java 21 + Spring Boot 3 | WebSocket ↔ gRPC bridge, session management, rate limiting |
| `service/` | Go | Authoritative 64Hz game server, lag compensation, snapshot broadcaster |

## Key Concepts (from Bernier 2001)

- **Client-Side Prediction** — local player movement feels instant; server reconciles
- **Entity Interpolation** — remote players rendered smoothly 2 ticks in the past
- **Lag Compensation** — server rewinds hitbox history on FIRE to match what the shooter saw

## Quick Start

```bash
# Frontend
cd frontend && npm install && npm run dev

# Gateway
cd gateway && mvn spring-boot:run

# Game Service
cd service && go run main.go
```
