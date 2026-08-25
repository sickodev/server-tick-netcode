# Agent: DevOps & Deploy
**ID:** `devops-deploy`
**Emoji:** 🚀
**Complexity:** Runs in parallel, activates fully at M7

---

## Mission
Containerise all three services and wire up free-tier deployments on Vercel, Koyeb, and Fly.io. Ensure every `git push` to `main` results in a live, working game reachable from any browser.

---

## Owns
- `frontend/Dockerfile` *(optional — Vercel builds from source)*
- `gateway/Dockerfile`
- `service/Dockerfile`
- `service/fly.toml`
- `docker-compose.yml` *(root — for local all-in-one dev)*
- `docs/deploy.md` *(deployment runbook)*

---

## Early Work (can start immediately)

### Local `docker-compose.yml`
Gets all three services running locally with a single `docker-compose up`:

```yaml
version: "3.9"
services:

  service:
    build: ./service
    ports:
      - "9090:9090"
    environment:
      GRPC_PORT: "9090"
      ARENA_W: "1200"
      ARENA_H: "800"

  gateway:
    build: ./gateway
    ports:
      - "8080:8080"
    environment:
      GO_SERVICE_HOST: service
      GO_SERVICE_PORT: "9090"
      SERVER_PORT: "8080"
    depends_on:
      - service

  frontend:
    build:
      context: ./frontend
      args:
        VITE_GATEWAY_URL: ws://localhost:8080/ws
    ports:
      - "5173:80"
    depends_on:
      - gateway
```

---

## Dockerfiles

### `service/Dockerfile`
```dockerfile
FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -o gameserver main.go

FROM alpine:3.19
WORKDIR /app
COPY --from=build /app/gameserver .
EXPOSE 9090
ENTRYPOINT ["./gameserver"]
```

### `gateway/Dockerfile`
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### `frontend/Dockerfile` (for docker-compose only)
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_GATEWAY_URL
ENV VITE_GATEWAY_URL=$VITE_GATEWAY_URL
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

---

## `service/fly.toml`
```toml
app = "netcode-service"
primary_region = "sin"

[build]
  dockerfile = "Dockerfile"

[env]
  GRPC_PORT = "9090"
  ARENA_W   = "1200"
  ARENA_H   = "800"

[[services]]
  internal_port = 9090
  protocol      = "tcp"
  min_machines_running = 1

  [[services.ports]]
    port = 9090
```

---

## Deployment Steps

### Step 1 — Go Service → Fly.io
```bash
winget install Flyio.Flyctl
flyctl auth login
cd service
flyctl launch   # follow prompts, region: sin (Singapore)
flyctl deploy
flyctl status   # note hostname: netcode-service.fly.dev
```

### Step 2 — Gateway → Koyeb
1. Go to [koyeb.com](https://koyeb.com) → New Service → GitHub
2. Repository: this repo, branch: `main`, build context: `gateway/`
3. Set environment variables:
   - `GO_SERVICE_HOST` = `netcode-service.fly.dev`
   - `GO_SERVICE_PORT` = `9090`
4. Port: `8080`. Deploy.
5. Note hostname: `gateway-xxx.koyeb.app`

### Step 3 — Frontend → Vercel
1. Go to [vercel.com](https://vercel.com) → Import Git Repository
2. Root directory: `frontend/`
3. Framework: Vite (auto-detected)
4. Add environment variable:
   - `VITE_GATEWAY_URL` = `wss://gateway-xxx.koyeb.app/ws`
5. Deploy. Note URL: `netcode.vercel.app`

---

## Responsibilities
- `docker-compose up` must start all three services locally with zero manual steps
- All environment variables are documented in `docs/deploy.md` — no undocumented config
- Fly.io `min_machines_running = 1` must be set — prevents cold starts during demo
- Koyeb, Fly.io deploy must be triggered by a git push (not manual CLI deploy) for production
- `docker-compose.yml` is the local dev standard — all agents use it for integration testing

---

## Collaborates With
| Agent | Why |
|---|---|
| `proto-contract` | Proto codegen must work inside Docker build (no local protoc needed) |
| `qa-integration` | Provides `docker-compose up` as the integration test environment |
| All agents | Each agent's story must work inside their respective Docker container |

---

## Definition of Done Gate
- [ ] `docker-compose up` → all three services healthy, game playable at `localhost:5173`
- [ ] `docker-compose up` → no manual steps required (env vars all have defaults or are in `.env`)
- [ ] Go service deployed to Fly.io, `grpcurl` confirms gRPC is reachable
- [ ] Gateway deployed to Koyeb, `/health` returns 200
- [ ] Frontend deployed to Vercel, two browsers can play the game end-to-end
- [ ] `docs/deploy.md` runbook is accurate (another person can follow it cold)
