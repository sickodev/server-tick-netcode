# Agent: Frontend Game Loop
**ID:** `frontend-gameloop`
**Emoji:** 🟢
**Complexity:** Straightforward

---

## Mission
Build the rendering foundation of the game: a blank canvas, a visible player circle, local WASD movement, and mouse-tracked aim direction. No networking. This is the visual and loop bedrock every other frontend story builds on.

---

## Owns
- `frontend/src/game/GameEngine.ts`
- `frontend/src/game/InputHandler.ts`
- `frontend/src/game/Renderer.ts`
- `frontend/src/game/constants.ts` *(create this file)*
- `frontend/src/components/GameCanvas.tsx`
- `frontend/src/App.tsx` *(strip Vite defaults)*

---

## Stories
| ID | Title | Complexity |
|---|---|---|
| F01 | Mount a blank game canvas | `XS` |
| F02 | Draw a stationary player circle | `XS` |
| F03 | Move the player locally with WASD | `S` |
| F04 | Show aim direction with mouse-tracked line | `XS` |

---

## Skills
- `requestAnimationFrame` game loop pattern (fixed `deltaTime` calculation)
- Canvas 2D API (`clearRect`, `arc`, `fillStyle`, `beginPath`, `lineTo`, `stroke`)
- Keyboard event capture (`keydown`, `keyup`, held-key tracking via a `Set<string>`)
- Mouse position tracking relative to canvas (`canvas.getBoundingClientRect()`)
- TypeScript class design

---

## Project Context

### Constants (define in `frontend/src/game/constants.ts`)
```ts
export const ARENA_W       = 1200;   // px
export const ARENA_H       = 800;    // px
export const PLAYER_RADIUS = 20;     // px
export const PLAYER_SPEED  = 200;    // px/sec — MUST match service/physics/constants.go
export const TICK_RATE     = 64;     // Hz
export const TICK_DURATION = 1000 / TICK_RATE; // ms
```

### Frame Loop Pattern
```ts
let lastTime = 0;
function loop(now: number) {
  const dt = (now - lastTime) / 1000; // seconds
  lastTime = now;
  update(dt);
  draw();
  requestAnimationFrame(loop);
}
requestAnimationFrame(loop);
```

### Aim Angle Calculation
```ts
const dx = mouse.x - player.x;
const dy = mouse.y - player.y;
const aimAngle = Math.atan2(dy, dx); // radians
```

### Boundary Clamping
```ts
player.x = Math.max(PLAYER_RADIUS, Math.min(ARENA_W - PLAYER_RADIUS, player.x));
player.y = Math.max(PLAYER_RADIUS, Math.min(ARENA_H - PLAYER_RADIUS, player.y));
```

---

## Responsibilities
- Strip `App.tsx` of all Vite boilerplate (counter, logos, CSS)
- `GameCanvas.tsx` should mount the `<canvas>`, get a 2D context, and hand it to `Renderer`
- `GameEngine` bootstraps the loop and wires `InputHandler` → local state → `Renderer`
- `InputHandler` tracks held keys and live mouse position — it does **not** produce `UserCmd` yet (that's `frontend-networking`)
- `Renderer.draw()` clears the canvas first, then draws: arena boundary → player circle → aim line
- Canvas size must match `ARENA_W × ARENA_H` at all times (handle window resize)

---

## Collaborates With
| Agent | Why |
|---|---|
| `frontend-networking` | Hands off `GameEngine`, `InputHandler`, `Renderer` as working foundation |
| `ux-design` | Receives visual spec: colors, player style, arena style |

---

## Definition of Done Gate
- [ ] Black canvas renders at `1200 × 800`
- [ ] White circle at centre, moves with WASD, clamps to arena
- [ ] Aim line tracks mouse in real-time
- [ ] `npm run build` passes with no TypeScript errors
- [ ] No `console.error` in browser DevTools during normal use
