# Agent: QA & Integration
**ID:** `qa-integration`
**Emoji:** 🧪
**Complexity:** Runs after each cross-service milestone

---

## Mission
Verify that all three services work correctly together at each integration point. Catch issues at the boundary between agents before they compound. Own the end-to-end test checklist and the final demo smoke test.

---

## Owns
- `.agents/integration-log.md` *(create and maintain — log of integration test results)*
- Any integration test scripts added to the root of the repo
- The final pre-demo smoke test checklist

---

## Integration Checkpoints

Run these in order as the relevant stories complete.

---

### Checkpoint 1: Go service + Gateway connected
**Trigger:** G05 (gateway connects to gRPC) + S07 (gRPC server running)

**Tests:**
- [ ] `grpcurl -plaintext localhost:9090 list` → `game.GameService` visible
- [ ] `GET localhost:8080/health` → `{ "status": "ok" }`
- [ ] Gateway log shows gRPC channel created on startup
- [ ] Kill Go service → gateway logs error, does not crash
- [ ] Restart Go service → gateway reconnects (if channel is lazy)

---

### Checkpoint 2: Full join flow
**Trigger:** G06 (stream opened on join) + S08 (JoinRequest handled)

**Tests:**
- [ ] Browser opens, WebSocket connects → gateway log: session connected
- [ ] Join message sent → Go log: `[join] player <id> spawned at (x, y)`
- [ ] `JoinResponse` arrives in browser (check DevTools WebSocket frames)
- [ ] Open two browsers → Go world has 2 players
- [ ] Close one browser → Go log: player removed within 1 tick

---

### Checkpoint 3: Movement visible across two clients
**Trigger:** S10 (snapshots broadcast) + G08 (snapshots forwarded to WebSocket) + F08 (snapshots rendered)

**Tests:**
- [ ] Move WASD in browser A → circle moves in browser B
- [ ] Snapshot rate in DevTools: ~64 inbound frames/sec
- [ ] `usercmd` rate in DevTools: ~60 outbound frames/sec
- [ ] Disconnect browser A → browser B stops seeing that player within 1 tick

---

### Checkpoint 4: Latency compensation working
**Trigger:** F09-F11 (prediction + interp) + S11 (history buffer)

**Tests:**
- [ ] Open Chrome DevTools → Network → throttle to "Slow 3G" (300ms RTT)
- [ ] Local player WASD movement still feels instant (no perceived delay)
- [ ] Remote player in browser B moves smoothly (no teleporting)
- [ ] Debug overlay shows prediction error < 2px under throttle
- [ ] Debug overlay shows ping ~300ms under throttle

---

### Checkpoint 5: Shooting and hit detection
**Trigger:** F12 (fire flag) + S12 (bullets spawned) + S13 (lag compensation hit detection)

**Tests:**
- [ ] Click in browser A → bullet appears in both browsers
- [ ] Aim at browser B player, click → health decreases in both browsers
- [ ] Fire cooldown: rapid-click in DevTools → only 1 `fire: true` per 10 ticks
- [ ] Browser B player reaches 0 health → disappears, reappears after 3 seconds
- [ ] "Behind cover" edge case: intentionally move B behind arena wall just before A fires — confirm hit still registers from A's perspective

---

### Final Demo Smoke Test
Run this immediately before every demo.

**Setup:** Two browsers on same machine, no network throttle.

- [ ] Both browsers show black canvas, no errors in DevTools
- [ ] WebSocket connected (green indicator or DevTools check)
- [ ] Both players visible and distinguishable by color
- [ ] WASD movement in each browser is smooth for the other player
- [ ] Aiming and firing works in both directions
- [ ] Bullets visible and tracked correctly
- [ ] Health bars update on hit
- [ ] Player respawns after death
- [ ] Debug overlay readable and accurate (toggle with `` ` ``)
- [ ] Close and reopen one browser → seamless rejoin

---

## Responsibilities
- Log all test results with timestamp and story version in `integration-log.md`
- Do not mark a checkpoint as passed if any test fails — escalate to the owning agent
- Latency simulation uses Chrome DevTools Network throttle (no extra tooling required)
- "Behind cover" test is mandatory before S13 is marked done
- Take a screenshot of the final demo state and commit it to `docs/demo-screenshot.png`

---

## Collaborates With
All agents — this agent is the integration gate between every service boundary.

---

## Definition of Done Gate (for the overall MVP)
- [ ] All 5 checkpoints passed
- [ ] Final demo smoke test passed with 0 failures
- [ ] `integration-log.md` has a passing entry for each checkpoint
- [ ] Demo screenshot committed
