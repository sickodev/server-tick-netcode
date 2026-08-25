# Visual Design Specification: Server-Tick Netcode

**Author:** UX Design Agent (`ux-design`)  
**Status:** Approved & Official  
**Scope:** Canvas 2D rendering, visual language, UI/HUD, and game feel animations  
**Implementation Targets:** `frontend-gameloop` (F01–F04), `frontend-polish` (F12–F13)

---

## 1. Overview & Design Philosophy

The visual design for *server-tick-netcode* establishes a high-contrast, minimalist cyber-arena aesthetic. The goal is to provide **unambiguous visual clarity** during fast-paced multiplayer gameplay while giving immediate, satisfying sensory feedback for network prediction, hit registration, and combat actions.

### Core Principles
1. **Instant Readability:** Local player, enemies, bullets, and boundaries must be distinguishable within milliseconds, even in peripheral vision.
2. **Zero Layout Latency:** All HUD elements and debug metrics are rendered directly on the Canvas 2D context to avoid DOM-layout desync and maintain lockstep rendering with physics frames.
3. **Intentional Game Feel:** Micro-animations (muzzle flashes, hit strobes, elimination bursts) provide tactile feedback that masks network roundtrips and prediction corrections.

---

## 2. Color Palette Reference

| Token Name | Hex / RGBA Value | Semantic Role & Usage | Contrast & Accessibility |
|---|---|---|---|
| `COLOR_BG` | `#0a0a0f` | Arena background canvas fill | Deep near-black with subtle blue undertone; maximizes contrast with bright neon entities |
| `COLOR_GRID` | `rgba(255, 255, 255, 0.04)` | Subtle 100px grid guidelines | Gives spatial depth and velocity reference without visual clutter |
| `COLOR_BORDER` | `#3d3d5c` | Arena boundary perimeter stroke (2px) | Clearly demarcates playable arena bounds (`1200 × 800`) |
| `COLOR_LOCAL_PLAYER` | `#00e5ff` | Local player circle fill (Cyan) | Bright, recognizable hero color indicating self-identity |
| `COLOR_LOCAL_OUTLINE` | `#ffffff` | Local player circle outline stroke (2px) | Reinforces focus on local character |
| `COLOR_AIM_LINE` | `#ffffff` | Local player aim trajectory indicator | Sharp white line pointing from player center toward cursor |
| `COLOR_REMOTE_PLAYER` | `#ff4757` | Remote enemy circle fill (Vibrant Red) | Instantly communicates hostile entity |
| `COLOR_BULLET` | `#ffd32a` | Projectile fill (Bright Golden Yellow) | High-luminance accent that tracks clearly across dark background |
| `COLOR_BULLET_TRAIL` | `rgba(255, 211, 42, α)` | Bullet motion trail (`α = 0.6, 0.4, 0.2`) | Enhances bullet trajectory readability at high velocity |
| `COLOR_HEALTH_FULL` | `#2ed573` | Health bar fill (Emerald Green, > 30% HP) | Clear affirmative vitality signal |
| `COLOR_HEALTH_LOW` | `#ff4757` | Health bar fill (Red, ≤ 30% HP) | Urgent danger warning state |
| `COLOR_HEALTH_BG` | `rgba(0, 0, 0, 0.6)` | Health bar background container | High-contrast backdrop under health fill |
| `COLOR_MUZZLE_FLASH` | `#ffdd00` | Firing ring expanding stroke | Crisp energetic spark feedback |
| `COLOR_HIT_FLASH` | `#ffffff` | Damage impact strobe fill (2 frames) | Instant visual hit confirmation |
| `COLOR_TEXT` | `#ffffff` | In-canvas HUD text, player tags | Crisp monospace font readability |
| `COLOR_DEBUG_BG` | `rgba(0, 0, 0, 0.75)` | Debug overlay rounded box fill | Dark translucent panel preventing gameplay obstruction |
| `COLOR_DEBUG_LABEL` | `#a4b0be` | Debug overlay metric labels | Muted cool-grey secondary text |
| `COLOR_DEBUG_VALUE` | `#00e5ff` | Debug overlay live values | Neon cyan matching primary telemetry palette |
| `COLOR_DEBUG_BORDER` | `rgba(0, 229, 255, 0.3)` | Debug overlay border outline (1px) | Subtle sci-fi HUD frame |

---

## 3. Entity Geometry & Styling Specifications

```
                       [ ID Tag: p_9a4f ]          (8px above health bar)
                         ████████░░                (Health Bar: 44px × 4px)
                            ┌───┐                  
                        /     ▲     \              
                       |    ( 0 )    | ───►        (Aim Line: 30px from radius)
                        \     │     /              
                            └───┘                  (Player Circle: radius 20px)
```

### 3.1 Arena Dimensions & Grid
- **Canvas Resolution:** `1200px` width × `800px` height (exact coordinate system `0,0` to `1200,800`).
- **Arena Background:** Filled with `COLOR_BG` (`#0a0a0f`).
- **Grid Layout:** Vertical and horizontal lines spaced every `100px` starting at `x = 100`, `y = 100`.
  - `strokeStyle`: `rgba(255, 255, 255, 0.04)`
  - `lineWidth`: `1px`
- **Arena Boundary:** Outer rectangle at `x = 1`, `y = 1`, `width = 1198`, `height = 798`.
  - `strokeStyle`: `COLOR_BORDER` (`#3d3d5c`)
  - `lineWidth`: `2px`

### 3.2 Local Player
- **Body:** Filled circle with radius `PLAYER_RADIUS = 20px`.
  - `fillStyle`: `COLOR_LOCAL_PLAYER` (`#00e5ff`)
  - `strokeStyle`: `COLOR_LOCAL_OUTLINE` (`#ffffff`)
  - `lineWidth`: `2px`
- **Aim Indicator Line:**
  - Start point: `(player.x + cos(aimAngle) * 20, player.y + sin(aimAngle) * 20)` (at edge of player circle).
  - End point: `(player.x + cos(aimAngle) * 50, player.y + sin(aimAngle) * 50)` (length `30px`).
  - `strokeStyle`: `COLOR_AIM_LINE` (`#ffffff`)
  - `lineWidth`: `2px`
  - `lineCap`: `round`
- **Overhead Health Bar:**
  - Dimensions: `44px` width × `4px` height.
  - Position: Centered horizontally at `player.x - 22`, vertical offset `player.y - 32`.
  - Background container: `fillStyle = rgba(0, 0, 0, 0.6)`.
  - Fill bar: `width = 44 * (health / 100)`.
  - Fill color: `health > 30 ? '#2ed573' : '#ff4757'`.
- **Player Identifier Tag:**
  - Text: Short ID suffix (e.g. `ID.slice(-4)`).
  - Font: `10px 'Courier New', monospace`.
  - Alignment: `textAlign = 'center'`, `textBaseline = 'bottom'`.
  - Position: `(player.x, player.y - 36)`.
  - `fillStyle`: `COLOR_TEXT` (`#ffffff`).

### 3.3 Remote Players
- **Body:** Filled circle with radius `PLAYER_RADIUS = 20px`.
  - Normal state `fillStyle`: `COLOR_REMOTE_PLAYER` (`#ff4757`).
  - Hit flash state (2 frames on damage): `fillStyle = COLOR_HIT_FLASH` (`#ffffff`).
  - Outline: None for MVP (creates visual contrast vs local player).
- **Aim Indicator:** None rendered for remote players in MVP to reduce visual noise.
- **Overhead Health Bar & ID Tag:** Identical dimensions and positioning as local player.

### 3.4 Bullets & Trails
- **Bullet Body:**
  - Circle with radius `BULLET_RADIUS = 5px`.
  - `fillStyle`: `COLOR_BULLET` (`#ffd32a`).
- **Motion Trail:**
  - Bullets maintain a small history ring buffer of up to 3 previous positions `[pos-1, pos-2, pos-3]`.
  - Trailing circles rendered with decreasing radii and alphas:
    - Lag 1 (1 frame ago): Radius `4px`, `fillStyle = rgba(255, 211, 42, 0.6)`.
    - Lag 2 (2 frames ago): Radius `3px`, `fillStyle = rgba(255, 211, 42, 0.4)`.
    - Lag 3 (3 frames ago): Radius `2px`, `fillStyle = rgba(255, 211, 42, 0.2)`.

---

## 4. Visual Effects & Animation Timing (Game Feel)

All effect durations assume 60 FPS rendering (`1 frame ≈ 16.6ms`). Effects are driven by frame counters or delta-time progression.

| Effect Name | Trigger Event | Duration | Visual Specifications & Formula |
|---|---|---|---|
| **Muzzle Flash** | Local player fires (immediate client-side trigger) | 3 frames (~50ms) | Expanding concentric ring centered on player position.<br>• `radius = PLAYER_RADIUS + (frameAge * 8px)` (expands from 20px to 44px)<br>• `lineWidth = 3px`<br>• `strokeStyle = #ffdd00`<br>• `globalAlpha = Math.max(0, 1 - (frameAge / 3))` |
| **Hit Flash** | Player receives bullet damage | 2 frames (~33ms) | Remote player's circle fill switches from `#ff4757` to solid `#ffffff` (`COLOR_HIT_FLASH`) for 2 render ticks before reverting back. |
| **Elimination Burst** | Player HP drops to 0 | 5 frames (~83ms) | Expanding shockwave circle burst at point of elimination.<br>• `radius = 20px + (frameAge * 6px)` (expands from 20px to 50px)<br>• `lineWidth = 2.5px`<br>• `strokeStyle = #ff4757`<br>• `globalAlpha = Math.max(0, 1 - (frameAge / 5))` |
| **Respawn Grow** | Player respawns into arena | 10 frames (~166ms) | Scale-in animation from 0 to full scale with ease-out curve.<br>• `progress = frameAge / 10`<br>• `scale = 1 - Math.pow(1 - progress, 3)` (Cubic ease-out)<br>• `radius = PLAYER_RADIUS * scale`<br>• Initial `globalAlpha = 0.5 + (0.5 * scale)` |

---

## 5. In-Canvas HUD Layout

To guarantee synchronous rendering without DOM repaints, the HUD is drawn directly onto the canvas.

```
+-----------------------------------------------------------------------------+
|                                                      HP [████████████] 100  |
|                                                                             |
|                                                                             |
|                                                                             |
|                                                                             |
|                                                                             |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### Specifications
- **Anchor:** Top-right corner of the canvas.
- **Offsets:** `24px` from top border, `24px` from right border.
- **Components:**
  1. **Label:** `"HP"` — Font `14px 'Courier New', monospace`, `fillStyle = #ffffff`.
  2. **Health Gauge Container:** Width `120px`, Height `12px`, Background `rgba(0, 0, 0, 0.7)`, Border `1px solid #3d3d5c`.
  3. **Health Gauge Fill:** Width `120 * (localPlayer.health / 100)px`, Fill `health > 30 ? '#2ed573' : '#ff4757'`.
  4. **Value Text:** `"${health}"` — Font `14px 'Courier New', monospace`, `fillStyle = #ffffff`, right-aligned.

---

## 6. Developer Debug Overlay Specification

The debug overlay provides telemetry for network health, prediction accuracy, and tick synchronization.

```
┌──────────────────────────────────────────┐
│  NETWORK & ENGINE TELEMETRY              │
│  ──────────────────────────────────────  │
│  PING                     42 ms          │
│  PRED ERR                0.3 px          │
│  INTERP DLY               31 ms          │
│  SERVER TICK              4821           │
│  BUFFERED                 3 cmds         │
└──────────────────────────────────────────┘
```

### Specifications
- **Toggle Key:** Backtick (`` ` `` / `code === 'Backquote'`).
- **Position & Sizing:**
  - `x = 16px`, `y = 16px`
  - `width = 240px`, `height = 136px`
  - Corner radius: `6px`
- **Container Styling:**
  - `fillStyle`: `rgba(0, 0, 0, 0.75)`
  - `strokeStyle`: `rgba(0, 229, 255, 0.3)`
  - `lineWidth`: `1px`
- **Typography:**
  - Font: `12px 'Courier New', monospace`
  - Line height / row spacing: `18px`
- **Data Rows & Alignment:**
  - Left column (`x + 14`): Metric label in `COLOR_DEBUG_LABEL` (`#a4b0be`).
  - Right column (`x + 226`, `textAlign = 'right'`): Metric value in `COLOR_DEBUG_VALUE` (`#00e5ff`).
  
| Row Label | Format String | Description & Source |
|---|---|---|
| `PING` | `${ping} ms` | Roundtrip latency estimate (`Date.now() - lastSnapshot.timestamp`) |
| `PRED ERR` | `${predictionError.toFixed(1)} px` | Positional discrepancy between client prediction and server ack |
| `INTERP DLY` | `${interpDelay} ms` | Remote entity interpolation buffer delay (nominal: ~31ms) |
| `SERVER TICK` | `${serverTick}` | Authoritative tick sequence number from latest received snapshot |
| `BUFFERED` | `${bufferedCmdCount} cmds` | Number of unacknowledged user commands in prediction ring |

---

## 7. Canvas Render Pass Layering (Z-Index)

To prevent visual artifacts, `Renderer.ts` must execute draw operations in the following strict order:

```
┌────────────────────────────────────────────────────────┐
│  Layer 8: Debug Overlay Panel                          │  (Topmost)
│  Layer 7: In-Canvas Player HUD                         │
│  Layer 6: Muzzle Flash Particles & Rings               │
│  Layer 5: Local Player (Circle, Outline, Aim, Health)  │
│  Layer 4: Remote Players (Circles, Flashes, Health)    │
│  Layer 3: Elimination Bursts & Hit Particles           │
│  Layer 2: Bullet Trails & Bullet Circles               │
│  Layer 1: Arena Boundary & 100px Grid Lines            │
│  Layer 0: Canvas Clear & Background Fill               │  (Bottom)
└────────────────────────────────────────────────────────┘
```

---

## 8. Constants & Implementation Quick-Reference

For direct import/usage in `frontend/src/game/constants.ts`:

```ts
/** Canvas & World Dimensions */
export const ARENA_W = 1200;
export const ARENA_H = 800;

/** Player & Physics Metrics */
export const PLAYER_RADIUS = 20;
export const PLAYER_SPEED = 200; // px/sec (must match service/physics/constants.go)
export const BULLET_RADIUS = 5;
export const BULLET_SPEED = 600; // px/sec
export const AIM_LINE_LENGTH = 30;

/** Tick Rates */
export const TICK_RATE = 64;
export const TICK_DURATION = 1000 / TICK_RATE; // 15.625ms

/** Visual Color Palette */
export const COLOR_BG = '#0a0a0f';
export const COLOR_GRID = 'rgba(255, 255, 255, 0.04)';
export const COLOR_BORDER = '#3d3d5c';
export const COLOR_LOCAL_PLAYER = '#00e5ff';
export const COLOR_LOCAL_OUTLINE = '#ffffff';
export const COLOR_AIM_LINE = '#ffffff';
export const COLOR_REMOTE_PLAYER = '#ff4757';
export const COLOR_BULLET = '#ffd32a';
export const COLOR_HEALTH_FULL = '#2ed573';
export const COLOR_HEALTH_LOW = '#ff4757';
export const COLOR_HEALTH_BG = 'rgba(0, 0, 0, 0.6)';
export const COLOR_MUZZLE_FLASH = '#ffdd00';
export const COLOR_HIT_FLASH = '#ffffff';
export const COLOR_TEXT = '#ffffff';
export const COLOR_DEBUG_BG = 'rgba(0, 0, 0, 0.75)';
export const COLOR_DEBUG_BORDER = 'rgba(0, 229, 255, 0.3)';
export const COLOR_DEBUG_LABEL = '#a4b0be';
export const COLOR_DEBUG_VALUE = '#00e5ff';

/** Effect Durations (in frames at ~60fps) */
export const FRAMES_MUZZLE_FLASH = 3;
export const FRAMES_HIT_FLASH = 2;
export const FRAMES_ELIMINATION = 5;
export const FRAMES_RESPAWN = 10;
```
