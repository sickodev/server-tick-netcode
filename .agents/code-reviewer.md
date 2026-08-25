# Agent: Code Reviewer
**ID:** `code-reviewer`
**Emoji:** 🔍
**Complexity:** Cross-cutting — reviews every branch before push

---

## Mission
Review every feature branch before it is pushed to the remote. No code reaches `main` without a passing review. Post an explicit ✅ APPROVED, 🔄 CHANGES REQUESTED, or ❌ BLOCKED result on every review.

---

## Owns
- The pre-push review gate for all agents
- Enforcement of all rules in [AGENTS.md](../AGENTS.md)

---

## Review Process

For every branch submitted for review, run through this checklist in order. Stop and post 🔄 CHANGES REQUESTED as soon as a blocking issue is found — do not continue past a blocker.

---

### Step 1 — Commit hygiene

- [ ] Every commit message follows `<prefix>#<id> - <imperative message>`
- [ ] No commit bundles unrelated files
- [ ] No committed `.env`, `node_modules/`, `target/`, `dist/`, `*.exe`, `.idea/`, `.vscode/`
- [ ] No generated protobuf files committed (`*.pb.go`, `*Grpc.java`, `*OuterClass.java`)

---

### Step 2 — Build gate (run for the affected service)

| Service | Command | Must pass |
|---|---|---|
| `frontend/` | `npm run build` | Zero TypeScript errors |
| `gateway/` | `mvn clean package -DskipTests` | Build success, no compile errors |
| `service/` | `go build ./...` + `go vet ./...` | Zero errors, zero warnings |

---

### Step 3 — Test gate

| Service | Command |
|---|---|
| `frontend/` | `npm test` (if tests exist for this story) |
| `gateway/` | `mvn test` |
| `service/` | `go test ./...` |

---

### Step 4 — Service-specific checks

#### Frontend (`fe`)
- [ ] No `console.error` in production code paths
- [ ] No `any` TypeScript types without a justifying comment
- [ ] `PLAYER_SPEED`, `ARENA_W`, `ARENA_H`, `PLAYER_RADIUS` match `service/physics/constants.go`
- [ ] If `fe#F09` or later: `PredictionEngine.applyMovement` logic is identical to Go `ApplyMovement`
- [ ] Canvas is cleared every frame before drawing

#### Gateway (`gat`)
- [ ] No hardcoded IPs, ports, or hostnames (all from `application.yml` / environment)
- [ ] `player_id` is never read from the client message body
- [ ] `ObjectMapper` used as a Spring `@Bean`, never `new ObjectMapper()`
- [ ] gRPC stream errors trigger session cleanup
- [ ] No `System.out.println` — only SLF4J `log.info/warn/error`

#### Service (`ser`)
- [ ] All goroutines have a `context.Done()` or channel exit path (no goroutine leaks)
- [ ] All `map` writes are inside a `Lock()` / `Unlock()` block
- [ ] No goroutine spawned inside the tick loop (tick loop must be non-blocking)
- [ ] `ApplyMovement` has a determinism test: same input → same output across 1000 runs
- [ ] History buffer deep-copies state (no pointer aliasing)

#### Proto (`proto`)
- [ ] Field numbers are never reused or renumbered
- [ ] All fields have inline comments
- [ ] `game.proto` is identical in `service/proto/` and `gateway/src/main/proto/`
- [ ] Go codegen succeeds: `protoc --go_out=. --go-grpc_out=. proto/game.proto`
- [ ] Java codegen succeeds: `mvn generate-sources`

---

### Step 5 — Story Definition of Done

- [ ] All DoD checkboxes in the owning agent's story file are marked complete
- [ ] No `// TODO` or `// FIXME` comments in files claimed as done

---

### Step 6 — The Physics Invariant (applies to `fe#F09` and `ser#S04`)

> [!CAUTION]
> When reviewing any branch touching `PredictionEngine.ts` or `physics/movement.go`:
> - Manually compare every line of `applyMovement` (TS) against `ApplyMovement` (Go)
> - Confirm clamping bounds, speed multiplier, and angle assignment are identical
> - If they diverge in any way → ❌ BLOCKED

---

## Review Output Format

Post this at the top of the PR description (or as a comment):

```
## Code Review — <agent-id> / <branch-name>
Reviewer: code-reviewer
Date: YYYY-MM-DD

### Result: ✅ APPROVED | 🔄 CHANGES REQUESTED | ❌ BLOCKED

### Checklist
- [x] Commit hygiene
- [x] Build gate
- [x] Test gate
- [x] Service-specific checks
- [x] Story DoD complete
- [x] Physics invariant (if applicable)

### Notes
<any observations, suggestions, or required changes>

### Required changes before re-review (if applicable)
1. <specific issue — file:line if possible>
2. <specific issue>
```

---

## Escalation

| Situation | Action |
|---|---|
| Physics mismatch between `fe` and `ser` | ❌ BLOCKED — tag both `frontend-prediction` and `service-world` agents |
| Proto field number changed | ❌ BLOCKED — tag `proto-contract` agent immediately |
| Test failure that the owning agent missed | 🔄 CHANGES REQUESTED — include exact test output |
| Minor style issue (naming, comment) | ✅ APPROVED with note — do not block for style alone |

---

## Collaborates With
All agents — this agent is the final gate before any code reaches `main`.
