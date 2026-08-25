# Frontend Stories — React + Vite + TypeScript

Stories are ordered strictly by dependency. Each one should be completable and committable on its own before the next begins.

**Complexity scale:** `XS` < 1 hr · `S` 1–2 hr · `M` 2–4 hr · `L` 4–8 hr

---

## 🎨 Rendering Foundation

---

### STORY-F01 · Mount a blank game canvas
**Complexity:** `XS`

**Description**
Replace the Vite default `App.tsx` content with a full-screen `<canvas>` element. No game logic yet — just the canvas on screen with a black background.

**Definition of Done**
- [x] `GameCanvas.tsx` renders a `<canvas>` that fills the viewport
- [x] Background is solid black
- [x] No Vite boilerplate (default counter, logos) remains in the app
- [x] `npm run dev` shows only the black canvas, no console errors

---

### STORY-F02 · Draw a stationary player circle
**Complexity:** `XS`

**Description**
Inside `Renderer.ts`, implement a `draw()` method that paints a filled white circle at a hardcoded centre position. Call it from a basic `requestAnimationFrame` loop bootstrapped by `GameCanvas.tsx`.

**Definition of Done**
- [x] A white circle (~20px radius) is visible at the canvas centre
- [x] The frame loop runs via `requestAnimationFrame` (not `setInterval`)
- [x] `Renderer.ts` owns all canvas draw calls — nothing draws directly in the component
- [x] Circle redraws cleanly each frame (canvas cleared before draw)

---

### STORY-F03 · Move the player locally with WASD
**Complexity:** `S`

**Description**
Wire up `InputHandler.ts` to track which keys are held. Each frame, translate that into a `dx/dy` delta and move the player circle. No network, no server — pure local movement.

**Definition of Done**
- [x] `W A S D` keys move the circle up / left / down / right
- [x] Movement speed is a named constant (e.g. `PLAYER_SPEED = 200` px/sec)
- [x] Speed is frame-rate-independent (multiplied by `deltaTime` in seconds)
- [x] Player cannot move outside the canvas boundary
- [x] No keys held → circle stays still

---

### STORY-F04 · Show aim direction with a mouse-tracked line
**Complexity:** `XS`

**Description**
Track the mouse position in `InputHandler.ts`. In `Renderer.ts`, draw a short line from the player circle outward toward the mouse, indicating aim direction.

**Definition of Done**
- [x] A line (~30px) extends from the player circle toward the current mouse position
- [x] Line updates smoothly as mouse moves
- [x] `aimAngle` (radians) is derivable from player position + mouse position
- [x] Works correctly when the player has moved from centre (STORY-F03)

---

## 🌐 Networking

---

### STORY-F05 · Open a WebSocket connection to the gateway
**Complexity:** `S`

**Description**
Implement `NetworkClient.ts` to open a WebSocket connection to a configurable URL (read from `import.meta.env.VITE_GATEWAY_URL`). Log connect / disconnect / error events to the console. No messages sent or received yet.

**Definition of Done**
- [x] `NetworkClient` connects on instantiation
- [x] Console logs: `[ws] connected`, `[ws] disconnected`, `[ws] error: …`
- [x] Gateway URL is read from `.env.local` (`VITE_GATEWAY_URL=ws://localhost:8080/ws`)
- [x] `.env.local` is listed in `.gitignore`
- [x] Connection survives a gateway restart (auto-reconnect with 2s delay)

---

### STORY-F06 · Send a join message on connect
**Complexity:** `S`

**Description**
On WebSocket open, send a `{ type: "join", playerId: "<uuid>", name: "Player" }` JSON message. Generate the `playerId` with `crypto.randomUUID()` and store it for the session lifetime.

**Definition of Done**
- [x] A `join` JSON message is sent immediately after the connection opens
- [x] `playerId` is a valid UUID, consistent for the lifetime of the tab
- [x] The sent payload is logged to console for debugging
- [x] No crash if the gateway is not yet running (error is caught and logged)

---

### STORY-F07 · Send `usercmd` messages every frame
**Complexity:** `S`

**Description**
Each frame, package the current `InputHandler` state into a `UserCmd` JSON object and send it via `NetworkClient`. Increment a monotonic `seq` counter per command.

**Definition of Done**
- [x] Every frame sends `{ type: "usercmd", seq, timestamp, dx, dy, aimAngle, fire }` over WebSocket
- [x] `seq` starts at 1 and increments by 1 every frame
- [x] `timestamp` is `Date.now()`
- [x] Messages are only sent when the socket is in `OPEN` state
- [x] Network tab in DevTools shows a stream of small WebSocket frames

---

### STORY-F08 · Receive snapshots and render server positions
**Complexity:** `S`

**Description**
Parse incoming `{ type: "snapshot", ... }` messages in `NetworkClient`. Extract entity positions and pass them to `Renderer`. For now, **skip prediction** — just snap the local player directly to the server-authoritative position.

**Definition of Done**
- [x] Snapshot messages are parsed without error
- [x] Local player position updates to match server position on each received snapshot
- [x] Remote players (other `entities` in the snapshot) are drawn as grey circles
- [x] Bullets in the snapshot are drawn as small white dots
- [x] Console logs the `serverTick` and `ackSeq` of each received snapshot

---

## ⚡ Latency Compensation (Client-Side)

---

### STORY-F09 · Add client-side prediction for local player movement
**Complexity:** `M`

**Description**
Implement `PredictionEngine.ts`. Instead of waiting for server snapshots to move the local player, simulate movement locally the instant a key is pressed using the same physics constants as the server. Store each sent `UserCmd` in an unACKed buffer.

**Definition of Done**
- [x] Local player moves immediately on keypress — no visible delay even with artificial latency
- [x] `PredictionEngine` holds a buffer of sent `UserCmd`s not yet ACKed by the server
- [x] Movement constants (`PLAYER_SPEED`, arena bounds) match the Go service exactly
- [x] Unit test: given the same sequence of `UserCmd`s, `PredictionEngine` produces the same positions as the Go physics package
- [x] Old snap-to-server-position logic from STORY-F08 is removed for local player

---

### STORY-F10 · Add server reconciliation
**Complexity:** `M`

**Description**
When a snapshot arrives with `ackSeq`, compare the server's authoritative position for that command against what `PredictionEngine` predicted. If they differ, snap to the server position and re-simulate all buffered commands after `ackSeq` forward to the present.

**Definition of Done**
- [x] On snapshot receive: commands ≤ `ackSeq` are discarded from the buffer
- [x] If server position ≠ predicted position for `ackSeq`: snap + re-simulate remaining buffer
- [x] If positions match: buffer trimmed, no visual change
- [x] Prediction error (distance between predicted and server position) is stored for the debug overlay (STORY-F13)
- [x] Player movement feels smooth — no visible snapping under normal conditions

---

### STORY-F11 · Add entity interpolation for remote players
**Complexity:** `M`

**Description**
Implement `InterpolationBuffer.ts`. Instead of snapping remote players to the latest snapshot position, store the last N snapshots with timestamps and render remote entities at `now - interpDelay` by linearly interpolating between the two bracketing snapshots.

**Definition of Done**
- [x] `InterpolationBuffer` stores the last 8 snapshots per entity
- [x] Remote players are rendered at `renderTime = now - 2 × tickInterval` (≈ 31ms behind)
- [x] Position is linearly interpolated between the two snapshots bracketing `renderTime`
- [x] Remote players move smoothly with no teleporting under normal packet delivery
- [x] If only one snapshot is available (e.g. first connect), fall back to that position directly

---

## 🔫 Game Feel

---

### STORY-F12 · Add shooting — fire key sends fire flag
**Complexity:** `XS`

**Description**
Map left mouse click (or `Space`) to set `fire: true` in the `UserCmd` for that frame only. Play a simple canvas effect (a brief flash or expanding ring at the player position) as immediate visual feedback.

**Definition of Done**
- [x] Clicking or pressing `Space` sets `fire: true` on the next `UserCmd`
- [x] `fire` is `false` on all other frames
- [x] A brief local visual effect (flash / ring) plays instantly on fire — no waiting for server
- [x] Bullets fired appear from the snapshot once the server confirms them

---

### STORY-F13 · Add a debug overlay
**Complexity:** `S`

**Description**
Render a small HUD in the top-left corner of the canvas showing real-time netcode stats. Toggled with backtick (`` ` ``).

**Definition of Done**
- [ ] Overlay shows: **Ping** (ms), **Prediction error** (px, from STORY-F10), **Interp delay** (ms), **Server tick**, **Buffered cmds**
- [ ] Ping is estimated as `Date.now() - snapshot.timestamp` from the most recent snapshot
- [ ] Overlay is hidden by default, toggled with `` ` ``
- [ ] Text is readable on black background (white with slight shadow)
- [ ] Overlay has zero impact on game performance when hidden

---

## Summary

| Story | Title | Complexity |
|---|---|---|
| F01 | Mount a blank game canvas | `XS` |
| F02 | Draw a stationary player circle | `XS` |
| F03 | Move the player locally with WASD | `S` |
| F04 | Show aim direction with mouse-tracked line | `XS` |
| F05 | Open a WebSocket connection to the gateway | `S` |
| F06 | Send a join message on connect | `S` |
| F07 | Send `usercmd` messages every frame | `S` |
| F08 | Receive snapshots and render server positions | `S` |
| F09 | Add client-side prediction for local player | `M` |
| F10 | Add server reconciliation | `M` |
| F11 | Add entity interpolation for remote players | `M` |
| F12 | Add shooting — fire key sends fire flag | `XS` |
| F13 | Add a debug overlay | `S` |
