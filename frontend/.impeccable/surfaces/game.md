# Surface Brief: Game View

## 1. Job and Audience
- **Who:** Developers, engineering leads, and potential clients.
- **Context:** Accessing the interactive netcode demo in a web browser (desktop or mobile).
- **Need:** To understand, test, and evaluate the performance of three core latency compensation techniques (client prediction, entity interpolation, and server lag compensation).
- **Visitor Mode:** **Operate**. The visitor must be able to configure parameters, simulate unfavorable network conditions (ping, jitter, packet loss), and directly play the game to verify network alignment.

## 2. Outcome and Proof
- **Primary Task/Action:** Control a 2D player, shoot bullets, move behind walls, adjust artificial latency, and visually verify that shots hit remote players exactly as seen on screen.
- **Success:** Controls are zero-latency (predicted client-side) and fluid; remote player movement remains smooth (interpolated); hit registration on the authoritative server lines up precisely with the client's historical view (lag compensated).
- **Product-Specific Proof:** Live visual indicators showing the server's historical hitbox rewinding state when a client shoots, demonstrating the lag compensation mechanism in real time.

## 3. Selected Direction
- **Visual Authority:** **2D Brawl Stars**. Vibrant, chunky arcade aesthetic. Deep saturated primary colors (electric blue for self, cherry red for opponents, gold/purple for abilities). High-contrast dark borders around containers and text labels. Playful, heavy sans-serif typography.
- **Structural Thesis:** Split-panel layout:
  - *Main Panel (Right/Center):* The interactive HTML5 Canvas displaying the game world (players, obstacles, bullets) with floating Brawl Stars-style health and ammo bars.
  - *Control Panel (Left - Collapsible):* A chunky cartoon-console themed console sidebar holding simulated latency sliders (ping, packet loss) and live ping graphs.
- **Focal Moment:** Toggling the debug hitbox view, firing at a moving target under 300ms simulated latency, and watching the server's rewound hitbox overlay perfectly matching the client's bullet contact point.

## 4. Scope and Boundaries
- **Fidelity:** High-fidelity UI styling (buttons, containers, sliders) combined with custom 2D vector drawing for the canvas gameplay.
- **Breadth:** Single battle arena viewport with sidebar panels.
- **Interactivity:** Full game loop execution, keyboard + mouse controls (desktop), virtual touch joysticks (mobile), and real-time WebSocket state management.
- **Anti-goals:** No persistent backend login, no user authentication, no multi-level maps, and no 3D rendering.

## 5. States and Ranges
- **States:** Connection/Intro (nickname prompt), Connecting/Loading, Active Battle, Respawning, Disconnected/Reconnecting.
- **Ranges:** 1 to 10 simultaneous players, 0ms to 1000ms simulated network latency, 0% to 100% simulated packet loss.

## 6. Interaction and Layout
- **Game Controls:**
  - *Desktop:* WASD keys for movement, mouse tracking for aiming, mouse click to fire bullets.
  - *Mobile/Touch:* Two on-screen virtual joysticks (left joystick for movement, right joystick for direction aiming and drag-to-shoot firing).
- **HUD Layout:**
  - *Floating HUD (above players):* Segmented health bar (recovering over time) and 3-slotted recharging ammo indicator.
  - *Global HUD:* Match timer in top center, simple kill feed and scoreboard in top corners.
- **Affordances and Feedback:**
  - Satisfying bounce/scale animation when clicking buttons or virtual sticks.
  - Floating damage numbers spawning and drifting upwards when a player is hit.
  - Bullet hit impact sparks and particle trails.
  - Ghost outline overlay representing the uncompensated server-side player positions for visual debugging.

## 7. Constraints and Open Decisions
- **Constraints:**
  - Responsive layout adjusting gracefully from 1920x1080 desktop monitors to 5-inch mobile displays.
  - Canvas render loop running at target 60fps/120fps with minimal garbage collection.
- **Open Decisions:**
  - The precise character asset styling (e.g., specific circular avatar emojis or customized vector designs).
