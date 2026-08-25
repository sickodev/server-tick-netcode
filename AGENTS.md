# AGENTS.md — Team Working Agreement

This document is the **single source of rules** for all agents working on this project.
Every agent must read and follow this before writing a single line of code.

---

## 1. Prefix & Story ID Reference

Every commit, branch, and PR is tagged with a service prefix and story ID.

| Prefix | Service | Examples |
|---|---|---|
| `fe` | `frontend/` — React + Vite | `fe#F01`, `fe#F09` |
| `gat` | `gateway/` — Java Spring Boot | `gat#G01`, `gat#G06` |
| `ser` | `service/` — Go game server | `ser#S01`, `ser#S13` |
| `proto` | `service/proto/` — shared contract | `proto#G04` |
| `ops` | Docker, CI, deployments | `ops#M7` |
| `all` | Cross-cutting (agents, docs, root config) | `all#-` |

---

## 2. Commit Rules

### 2.1 Commit after every file change
Every meaningful file addition or modification gets its own commit — do not batch multiple unrelated files into one commit. One logical change = one commit.

```
✅  ser#S02 - add PlayerState, BulletState, WorldState structs
✅  ser#S02 - add WorldState mutex and arena constants
❌  ser#S02 - add world state stuff and some other things
```

### 2.2 Commit message format

```
<prefix>#<story-id> - <imperative verb phrase>
```

Rules:
- Imperative verb: `add`, `implement`, `fix`, `remove`, `rename`, `extract`, `wire`, `configure`
- All lowercase after the prefix tag
- No period at the end
- 72 character max
- If the change is not tied to a story (e.g. a typo fix): `<prefix>#- - <message>`

**Examples:**
```
fe#F01  - mount blank canvas in GameCanvas component
fe#F03  - add WASD movement with frame-rate-independent deltaTime
gat#G02 - register WebSocket handler at /ws with CORS config
gat#G06 - open bidirectional gRPC stream on player join
ser#S04 - implement deterministic ApplyMovement in physics package
ser#S11 - add 128-slot ring buffer for lag compensation history
proto#G04 - define UserCmd, Snapshot, and Play stream in game.proto
ops#-   - add Dockerfile for gateway service
all#-   - update AGENTS.md with commit prefix table
```

### 2.3 What not to commit
- `.env` or `.env.local` files (secrets/local overrides)
- Build artifacts (`target/`, `dist/`, `*.exe`, `node_modules/`)
- IDE files (`.idea/`, `.vscode/`)
- Generated protobuf files (they are regenerated at build time)

---

## 3. Branch Strategy

### 3.1 Naming convention
```
<prefix>/<story-id>-<short-kebab-desc>
```

**Examples:**
```
fe/F01-blank-canvas
fe/F09-F10-F11-prediction-interp     ← batch related stories on one branch
gat/G06-grpc-stream-per-player
ser/S11-S12-S13-lag-compensation
ops/docker-compose-setup
proto/game-proto-initial
```

### 3.2 Branch rules
- **Never commit directly to `main`** — all work happens on feature branches
- One branch per story or per tightly related story cluster (e.g. F09–F11 together)
- Branch off from the latest `main` — not from another feature branch
- Delete the branch after it is merged

### 3.3 Feature completion = push + PR
- **Commits are local** during active development — do not push individual commits
- **Push the entire branch at once** when all stories on that branch are complete and have passed code review
- Raise a PR targeting `main` immediately after pushing

---

## 4. Code Review — `code-reviewer` Agent

### 4.1 Role
The `code-reviewer` agent reviews **every branch before it is pushed**. No agent pushes without a passing review.

### 4.2 Review checklist (reviewer runs this for every PR)

**All services:**
- [ ] Commit messages follow the format in §2.2
- [ ] No committed secrets, build artifacts, or IDE files
- [ ] No `TODO` comments left in code that is claimed as "done"
- [ ] All story Definition of Done items are checked off in the story file

**Frontend (`fe`):**
- [ ] `npm run build` passes with zero TypeScript errors
- [ ] No `console.error` calls in production paths (only `console.warn` or `console.log` for debug)
- [ ] `PLAYER_SPEED`, `ARENA_W`, `ARENA_H`, `PLAYER_RADIUS` match `service/physics/constants.go` exactly
- [ ] `PredictionEngine.applyMovement` logic matches `physics.ApplyMovement` in Go exactly

**Gateway (`gat`):**
- [ ] `mvn clean package -DskipTests` passes
- [ ] No hardcoded IP addresses or ports (all from `application.yml` or env vars)
- [ ] `player_id` is never read from the client message body — always from the session

**Service (`ser`):**
- [ ] `go build ./...` and `go vet ./...` pass with zero warnings
- [ ] `go test ./...` passes
- [ ] No goroutine leaks — all spawned goroutines have a `context.Done()` exit path
- [ ] No direct `map` writes without a mutex lock

**Proto (`proto`):**
- [ ] Field numbers are never reused or renumbered
- [ ] Both Go and Java codegen succeed after any change
- [ ] `game.proto` is identical in `service/proto/` and `gateway/src/main/proto/`

### 4.3 Review output
The reviewer agent posts a review comment on the PR with one of:
- ✅ **APPROVED** — branch can be merged
- 🔄 **CHANGES REQUESTED** — list of specific issues, owning agent must fix before re-review
- ❌ **BLOCKED** — a critical invariant is violated (e.g. physics mismatch); escalate immediately

---

## 5. The Physics Invariant — Critical Rule

> [!CAUTION]
> `service/physics/movement.go → ApplyMovement()` and
> `frontend/src/game/PredictionEngine.ts → applyMovement()`
> **must produce identical results for identical inputs.**
>
> This is the most important correctness invariant in the project.
> Any divergence causes jittery reconciliation that looks like lag.
>
> **Rule:** Whenever `ser#S04` is merged, the `fe` agent working on `fe#F09`
> must immediately sync their `applyMovement` implementation.
> The reviewer agent will run a cross-language equivalence check before approving either branch.

---

## 6. Proto Contract Rules

> [!IMPORTANT]
> The `proto-contract` agent is the **sole owner** of `game.proto`.
> No other agent modifies any `.proto` file without a review comment from `proto-contract`.
>
> When proto changes:
> 1. `proto-contract` updates `game.proto` and bumps the changelog block at the top
> 2. `proto-contract` verifies Go codegen: `protoc --go_out=. --go-grpc_out=. proto/game.proto`
> 3. `proto-contract` verifies Java codegen: `mvn generate-sources`
> 4. Both generated sets are tested before downstream agents pick up the new stubs
> 5. Proto field numbers must **never** be reused — mark unused fields as `reserved`

---

## 7. Story Handoff Protocol

When an agent completes a story that another agent depends on, they must post a handoff note. Format:

```
HANDOFF: <story-id> complete
Owner:   <agent-id>
Branch:  <branch-name>
Outputs:
  - <file or API that downstream agents can now use>
  - <any constants, interfaces, or contracts exposed>
Notes:
  - <any gotchas or decisions made during implementation>
Unblocks: <downstream agent-id> to start <story-id>
```

**Example:**
```
HANDOFF: S04 complete
Owner:   service-world
Branch:  ser/S04-apply-movement
Outputs:
  - service/physics/movement.go → ApplyMovement(p, dx, dy, aimAngle, dt)
  - service/physics/constants.go → ArenaW=1200, ArenaH=800, PlayerSpeed=200, PlayerRadius=20
Notes:
  - Clamping uses PlayerRadius as inset on all four sides
  - dt is in seconds (not milliseconds)
Unblocks: frontend-prediction to start F09
```

---

## 8. Pull Request Rules

### 8.1 PR title format
```
<prefix>#<story-id(s)> — <short description>
```
```
fe#F01–F04 — rendering foundation (canvas, player, WASD, aim)
ser#S11–S14 — lag compensation: history buffer, bullets, hit detection
```

### 8.2 PR description must include
- **Stories covered** — list with checkboxes matching DoD
- **Test evidence** — what was run and passed
- **Screenshot or log snippet** — at least one piece of evidence it works
- **Reviewer mention** — always tag `code-reviewer`
- **Handoff note** — if other agents are unblocked by this PR

### 8.3 Merge rules
- Squash merge is **not allowed** — preserve the commit history (it traces to story IDs)
- Merge only after `code-reviewer` posts ✅ **APPROVED**
- Delete the feature branch after merge
- `main` must always be in a buildable state after every merge

---

## 9. General Conduct Rules

| Rule | Detail |
|---|---|
| **Own your stories** | Each agent owns their listed stories end-to-end — implementation, tests, and DoD sign-off |
| **No scope creep** | Do not modify files outside your ownership without explicit coordination with the owning agent |
| **Failing tests block commits** | Never commit code that fails `go test`, `mvn test`, or `npm run build` |
| **Constants are shared** | Any constant used in both `frontend/` and `service/` lives in **both** `constants.ts` and `constants.go` — they must be identical values |
| **No magic numbers** | Every number in the code must reference a named constant |
| **Log with context** | Every log line includes the agent's service prefix: `[ws]`, `[grpc]`, `[tick]`, `[hit]`, etc. |
| **Respect the dependency order** | Do not start a story if its prerequisite stories are not merged to `main` |
| **Ask before assuming** | If a story is ambiguous or a dependency is unclear, post a question in the handoff log rather than guessing |

---

## 10. Quick Reference

```
Start a story
  └── git checkout -b <prefix>/<story-id>-<desc>

After every file change
  └── git add <file> && git commit -m "<prefix>#<id> - <message>"

Story complete
  ├── run all tests
  ├── check all DoD items
  ├── request review from code-reviewer agent
  └── (on approval) git push origin <branch> && open PR

PR merged
  ├── post HANDOFF note if you unblock another agent
  └── git branch -d <branch>
```
