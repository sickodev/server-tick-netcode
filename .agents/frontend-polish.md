# Agent: Frontend Polish
**ID:** `frontend-polish`
**Emoji:** 🟢
**Complexity:** Straightforward

---

## Mission
Add the finishing gameplay touches to the frontend: shooting mechanics and a developer debug overlay. These are the last frontend stories before the demo is feature-complete.

---

## Owns
- Modifications to `InputHandler.ts` (fire detection)
- Modifications to `Renderer.ts` (muzzle flash effect)
- Debug overlay rendering inside `Renderer.ts`
- Modifications to `GameEngine.ts` (overlay toggle)

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| F12 | Add shooting — fire key sends fire flag | `XS` |
| F13 | Add a debug overlay | `S` |

---

## Skills
- Canvas 2D visual effects (arcs, alpha, `globalAlpha`, radial gradients)
- Event listeners for mouse click + keyboard
- Browser `performance.now()` for precise timing
- HUD text rendering on canvas (`fillText`, `font`, `textAlign`)

---

## Project Context

### Fire Flag (F12)
```ts
// In InputHandler — fire is true for exactly one frame on mousedown or Space
let fireThisFrame = false;
canvas.addEventListener('mousedown', () => { fireThisFrame = true; });
window.addEventListener('keydown', e => { if (e.code === 'Space') fireThisFrame = true; });

flush(): UserCmd {
  const cmd = { ..., fire: fireThisFrame };
  fireThisFrame = false; // consume — only true for ONE frame
  return cmd;
}
```

### Muzzle Flash Effect (F12)
```ts
// Draw an expanding semi-transparent ring at player position
// that fades over 3 frames (~50ms)
drawMuzzleFlash(player, frameAge):
  ctx.globalAlpha = Math.max(0, 1 - frameAge / 3)
  ctx.strokeStyle = '#ffdd00'
  ctx.lineWidth = 3
  ctx.beginPath()
  ctx.arc(player.x, player.y, PLAYER_RADIUS + frameAge * 8, 0, Math.PI * 2)
  ctx.stroke()
  ctx.globalAlpha = 1
```

### Debug Overlay (F13)

Toggle with `` ` `` (backtick). Rendered in the top-left corner.

```
┌─────────────────────────┐
│ PING        42 ms        │
│ PRED ERR    0.3 px       │
│ INTERP DLY  31 ms        │
│ SERVER TICK 4821         │
│ BUFFERED    3 cmds       │
└─────────────────────────┘
```

```ts
// Ping estimate
const ping = Date.now() - lastSnapshot.timestamp;

// Overlay rendering
ctx.fillStyle = 'rgba(0,0,0,0.6)'
ctx.fillRect(10, 10, 200, 110)
ctx.fillStyle = '#00ff88'
ctx.font = '13px monospace'
ctx.fillText(`PING        ${ping} ms`, 20, 30)
// ... etc
```

---

## Responsibilities
- `fire` must be `true` for exactly **one** frame per click/keypress — never held
- Muzzle flash plays immediately on fire input (client-side feedback, no server wait)
- Bullets appear on screen only after server confirms them in a snapshot
- Overlay is completely invisible (including its background) when toggled off
- All overlay values come from data already available in `GameEngine` state — no new network calls

---

## Collaborates With
| Agent | Why |
|---|---|
| `frontend-prediction` | Reads `predictionError` value for overlay |
| `frontend-networking` | Reads `lastSnapshot.serverTick` and `lastSnapshot.timestamp` for overlay |
| `ux-design` | Overlay color scheme and font choice approved by UX agent |
| `qa-integration` | Overlay values verified to be accurate in integration test |

---

## Definition of Done Gate
- [ ] Clicking fires exactly once per click (verified by watching `usercmd` stream in DevTools)
- [ ] Muzzle flash appears and fades within ~3 frames
- [ ] Backtick toggles overlay on/off without any performance impact
- [ ] All 5 overlay values display correct real-time data
- [ ] `npm run build` passes, no TS errors
