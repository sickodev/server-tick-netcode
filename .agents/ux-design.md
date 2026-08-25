# Agent: UX Design
**ID:** `ux-design`
**Emoji:** 🎨
**Complexity:** Runs in parallel with engineering agents

---

## Mission
Own the visual language of the game. Define colors, typography, visual effects, and game feel. Deliver a polished design spec that the `frontend-gameloop` and `frontend-polish` agents implement. The demo must look intentional, not like a prototype.

---

## Owns
- `.agents/design-spec.md` *(create and maintain — this is the design handoff doc)*
- Visual decisions for: arena, players, bullets, HUD, debug overlay
- Game feel decisions: muzzle flash, hit feedback, death effect, respawn animation

---

## Deliverables (not code — design decisions)

### Color Palette
Define a minimal, cohesive palette. Example starting point (agent should refine):

| Element | Color | Notes |
|---|---|---|
| Arena background | `#0a0a0f` | Near-black, slightly blue |
| Arena boundary | `#1e1e2e` | Subtle grid or border |
| Local player | `#00e5ff` | Cyan — clearly distinguishable |
| Remote players | `#ff4757` | Red — immediately reads as "enemy" |
| Bullets | `#ffd32a` | Yellow — high contrast on dark background |
| Health bar (full) | `#2ed573` | Green |
| Health bar (low) | `#ff4757` | Red, same as enemy color |
| UI text | `#ffffff` | White, `monospace` font |
| Debug overlay bg | `rgba(0,0,0,0.7)` | Semi-transparent |
| Debug overlay text | `#00e5ff` | Matches local player color |

### Player Visual Design
- **Shape:** Circle (radius 20px) with a directional notch (the aim indicator)
- **Local player:** Filled cyan circle + white outline (2px) + aim line (30px, white)
- **Remote player:** Filled red circle, no aim line visible for MVP
- **Health bar:** Thin bar (4px height, 44px width) above the player circle
- **Name tag:** Small white monospace text 8px above the health bar (no name for MVP — use short player ID suffix)

### Arena Design
- Solid dark background (`#0a0a0f`)
- Subtle grid lines every 100px (`rgba(255,255,255,0.04)`) — gives depth without clutter
- Arena boundary: a 2px `#3d3d5c` rectangle at `(0,0,1200,800)`
- No walls or obstacles for MVP

### Bullet Design
- Circle, radius 5px, color `#ffd32a`
- Leave a short trail (3 previous positions at decreasing alpha) for readability at speed

### Effects
| Effect | Trigger | Visual |
|---|---|---|
| Muzzle flash | Player fires | Expanding ring, `#ffdd00`, fades over 3 frames |
| Hit flash | Enemy player is hit | Remote player circle flashes white for 2 frames |
| Elimination | Player health → 0 | Expanding circle burst, `#ff4757`, 5 frames |
| Respawn | Player re-enters arena | Circle grows from 0 to full radius over 10 frames |

### HUD (in-canvas, not HTML overlay)
Render directly on canvas. Position: top-right corner.
```
                           HP  ████████░░  75
```
- Health bar only shown for local player
- No kill feed for MVP (too complex)

### Debug Overlay (inform `frontend-polish`)
- Background: `rgba(0,0,0,0.7)` rounded rect
- Font: `13px 'Courier New', monospace`
- Text color: `#00e5ff`
- Labels: left-aligned, values: right-aligned at a fixed column

---

## Responsibilities
- Deliver `design-spec.md` before `frontend-gameloop` starts F02 (so they draw the right colors)
- All colors and measurements are in the spec before the corresponding story is implemented
- Review Renderer output at each frontend milestone — raise visual issues before the story is marked done
- The game must look like a real product, not a tutorial canvas demo

---

## Collaborates With
| Agent | Why |
|---|---|
| `frontend-gameloop` | Provides color/shape spec before F02 |
| `frontend-polish` | Provides effect specs (muzzle flash, HUD) before F12-F13 |
| `qa-integration` | Visual regression check — screenshot comparison before demo |

---

## Definition of Done Gate
- [ ] `design-spec.md` committed with all colors, measurements, and effect descriptions
- [ ] Local and remote player are visually distinguishable at a glance
- [ ] Bullets are visible and readable at full speed
- [ ] The arena looks like an intentional game environment
- [ ] The debug overlay is readable and doesn't obscure gameplay
- [ ] Demo screenshot looks presentable to a non-technical audience
