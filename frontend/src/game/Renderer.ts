import { ARENA_H, ARENA_W, PLAYER_RADIUS } from './constants';
import { BulletState, EntityState } from './types';

/**
 * Visual styling configuration for the top-down 2D canvas renderer.
 */
export const RENDER_CONFIG = {
  /** Canvas background fill color representing deep space / dark arena */
  BACKGROUND_COLOR: '#0a0a0f',
  /** Arena boundary stroke color */
  ARENA_BORDER_COLOR: '#1e293b',
  /** Arena boundary stroke line width in pixels */
  ARENA_BORDER_WIDTH: 2,
  /** Grid overlay line color for spatial orientation */
  GRID_COLOR: 'rgba(255, 255, 255, 0.03)',
  /** Grid cell spacing in pixels */
  GRID_SIZE: 50,
  /** Local player primary fill color (vibrant cyan) */
  PLAYER_LOCAL_COLOR: '#00e5ff',
  /** Remote player primary fill color (vibrant red) */
  PLAYER_REMOTE_COLOR: '#ff4757',
  /** Player circle outer stroke color */
  PLAYER_OUTLINE_COLOR: '#ffffff',
  /** Player circle outer stroke width in pixels */
  PLAYER_OUTLINE_WIDTH: 2,
  /** Directional aim line stroke color */
  AIM_LINE_COLOR: '#ffffff',
  /** Directional aim line length extending from player center in pixels */
  AIM_LINE_LENGTH: 30,
  /** Active projectile bullet fill color (vibrant yellow) */
  BULLET_COLOR: '#ffd32a',
  /** Active projectile bullet rendering radius in pixels */
  BULLET_RADIUS: 4,
  /** Text color for player identification labels */
  PLAYER_LABEL_COLOR: '#ffffff',
  /** Typography style for player identification labels */
  PLAYER_LABEL_FONT: '11px monospace',
  /** Muzzle flash expanding ring maximum expansion radius in pixels */
  MUZZLE_FLASH_MAX_RADIUS: 24,
  /** Muzzle flash animation lifespan in milliseconds */
  MUZZLE_FLASH_DURATION_MS: 120,
} as const;

/**
 * Represents a transient local visual particle or ring animation effect.
 */
export interface VisualEffect {
  /** Discriminator of the visual effect */
  type: 'muzzle_flash';
  /** Horizontal origin coordinate in pixels */
  x: number;
  /** Vertical origin coordinate in pixels */
  y: number;
  /** Firing aim direction angle in radians */
  angle: number;
  /** Epoch timestamp in milliseconds when the effect was initiated */
  startTime: number;
  /** Duration in milliseconds before effect expires */
  duration: number;
}

/**
 * 2D Canvas rendering engine responsible for painting the game arena, local player,
 * interpolated remote players, extrapolated projectiles, and particle visual effects.
 *
 * Owns all direct HTML5 Canvas 2D context drawing operations.
 */
export class Renderer {
  private ctx: CanvasRenderingContext2D;

  /** Active transient visual effects (muzzle flashes, expanding rings) */
  private visualEffects: VisualEffect[] = [];

  /**
   * Initializes the Renderer with the target 2D rendering context.
   *
   * @param ctx - The 2D rendering context obtained from the HTML5 canvas element.
   */
  constructor(ctx: CanvasRenderingContext2D) {
    this.ctx = ctx;
  }

  /**
   * Clears the entire canvas viewport and paints the solid dark arena background.
   */
  public clear(): void {
    this.ctx.fillStyle = RENDER_CONFIG.BACKGROUND_COLOR;
    this.ctx.fillRect(0, 0, ARENA_W, ARENA_H);
  }

  /**
   * Renders the arena background grid and bounding borders.
   * Gives players visual spatial references and delineates playable bounds.
   */
  public drawArena(): void {
    const { ctx } = this;

    // Draw background grid lines for spatial reference
    ctx.strokeStyle = RENDER_CONFIG.GRID_COLOR;
    ctx.lineWidth = 1;
    ctx.beginPath();

    // Vertical grid lines
    for (let x = RENDER_CONFIG.GRID_SIZE; x < ARENA_W; x += RENDER_CONFIG.GRID_SIZE) {
      ctx.moveTo(x, 0);
      ctx.lineTo(x, ARENA_H);
    }

    // Horizontal grid lines
    for (let y = RENDER_CONFIG.GRID_SIZE; y < ARENA_H; y += RENDER_CONFIG.GRID_SIZE) {
      ctx.moveTo(0, y);
      ctx.lineTo(ARENA_W, y);
    }
    ctx.stroke();

    // Draw outer arena perimeter boundary
    ctx.strokeStyle = RENDER_CONFIG.ARENA_BORDER_COLOR;
    ctx.lineWidth = RENDER_CONFIG.ARENA_BORDER_WIDTH;
    ctx.strokeRect(0, 0, ARENA_W, ARENA_H);
  }

  /**
   * Draws a player circle entity with specified fill, outline, and optional aim indicator.
   *
   * @param x            - Horizontal center position in pixels.
   * @param y            - Vertical center position in pixels.
   * @param radius       - Radius of the player circle in pixels (defaults to PLAYER_RADIUS).
   * @param fillColor    - Fill color string (e.g. hex or rgba).
   * @param outlineColor - Outline stroke color string.
   * @param aimAngle     - Optional aim direction angle in radians (0 = facing right).
   * @param label        - Optional text label to render above the player entity.
   */
  public drawPlayer(
    x: number,
    y: number,
    radius: number = PLAYER_RADIUS,
    fillColor: string = RENDER_CONFIG.PLAYER_LOCAL_COLOR,
    outlineColor: string = RENDER_CONFIG.PLAYER_OUTLINE_COLOR,
    aimAngle?: number,
    label?: string
  ): void {
    const { ctx } = this;

    ctx.save();

    // Draw player body circle
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2, false);
    ctx.fillStyle = fillColor;
    ctx.fill();

    // Draw outer boundary ring
    ctx.lineWidth = RENDER_CONFIG.PLAYER_OUTLINE_WIDTH;
    ctx.strokeStyle = outlineColor;
    ctx.stroke();

    // Draw directional aim line if an aim angle is provided
    if (aimAngle !== undefined) {
      const endX = x + Math.cos(aimAngle) * RENDER_CONFIG.AIM_LINE_LENGTH;
      const endY = y + Math.sin(aimAngle) * RENDER_CONFIG.AIM_LINE_LENGTH;

      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(endX, endY);
      ctx.strokeStyle = RENDER_CONFIG.AIM_LINE_COLOR;
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    // Render player label above the entity circle if provided
    if (label) {
      ctx.fillStyle = RENDER_CONFIG.PLAYER_LABEL_COLOR;
      ctx.font = RENDER_CONFIG.PLAYER_LABEL_FONT;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      ctx.fillText(label, x, y - radius - 6);
    }

    ctx.restore();
  }

  /**
   * Renders all active remote player entities received in server snapshots.
   *
   * @param entities - Array of remote EntityState objects to draw.
   */
  public drawRemotePlayers(entities: EntityState[]): void {
    for (const entity of entities) {
      const label = entity.id ? `P-${entity.id.slice(0, 4)}` : 'Remote';
      this.drawPlayer(
        entity.x,
        entity.y,
        PLAYER_RADIUS,
        RENDER_CONFIG.PLAYER_REMOTE_COLOR,
        RENDER_CONFIG.PLAYER_OUTLINE_COLOR,
        entity.angle,
        label
      );
    }
  }

  /**
   * Renders all active projectiles in flight, extrapolating their positions forward
   * based on projectile velocity (vx, vy) and elapsed time since snapshot reception.
   *
   * Story F12 / Latency Compensation:
   * Extrapolating bullets between snapshot arrivals eliminates projectile stutter
   * and renders smooth projectile trajectories across screen repaints.
   *
   * @param bullets              - Array of BulletState objects from authoritative server snapshot.
   * @param extrapolationSeconds - Elapsed delta time in seconds since the snapshot was received.
   */
  public drawBullets(bullets: BulletState[], extrapolationSeconds: number = 0): void {
    const { ctx } = this;

    ctx.save();
    ctx.fillStyle = RENDER_CONFIG.BULLET_COLOR;

    for (const bullet of bullets) {
      // Extrapolate position along velocity vector: pos = pos0 + velocity * dt
      const bulletX = bullet.x + (bullet.vx || 0) * extrapolationSeconds;
      const bulletY = bullet.y + (bullet.vy || 0) * extrapolationSeconds;

      // Draw bullet head circle
      ctx.beginPath();
      ctx.arc(bulletX, bulletY, RENDER_CONFIG.BULLET_RADIUS, 0, Math.PI * 2, false);
      ctx.fill();

      // Draw subtle motion streak if bullet possesses significant velocity
      if (bullet.vx !== 0 || bullet.vy !== 0) {
        const speed = Math.hypot(bullet.vx, bullet.vy);
        if (speed > 0) {
          const streakLength = 8;
          const nx = bullet.vx / speed;
          const ny = bullet.vy / speed;

          ctx.beginPath();
          ctx.moveTo(bulletX, bulletY);
          ctx.lineTo(bulletX - nx * streakLength, bulletY - ny * streakLength);
          ctx.strokeStyle = RENDER_CONFIG.BULLET_COLOR;
          ctx.lineWidth = 2;
          ctx.stroke();
        }
      }
    }

    ctx.restore();
  }

  /**
   * Triggers an immediate local muzzle flash and expanding ring effect at the player's weapon muzzle.
   *
   * @param playerX  - Horizontal coordinate of the shooting player.
   * @param playerY  - Vertical coordinate of the shooting player.
   * @param aimAngle - Weapon aim direction in radians.
   */
  public addMuzzleFlash(playerX: number, playerY: number, aimAngle: number): void {
    // Offset muzzle flash origin to player circle circumference
    const muzzleX = playerX + Math.cos(aimAngle) * PLAYER_RADIUS;
    const muzzleY = playerY + Math.sin(aimAngle) * PLAYER_RADIUS;

    this.visualEffects.push({
      type: 'muzzle_flash',
      x: muzzleX,
      y: muzzleY,
      angle: aimAngle,
      startTime: performance.now(),
      duration: RENDER_CONFIG.MUZZLE_FLASH_DURATION_MS,
    });
  }

  /**
   * Paints and advances active transient visual effects (muzzle flashes, expanding shock rings).
   *
   * @param currentTime - High-resolution timestamp in milliseconds.
   */
  public drawEffects(currentTime: number = performance.now()): void {
    const { ctx } = this;
    const activeEffects: VisualEffect[] = [];

    for (const effect of this.visualEffects) {
      const elapsed = currentTime - effect.startTime;
      if (elapsed >= effect.duration) {
        continue; // Effect expired
      }

      activeEffects.push(effect);
      const progress = elapsed / effect.duration; // 0.0 to 1.0
      const opacity = 1.0 - progress;

      ctx.save();

      if (effect.type === 'muzzle_flash') {
        // Expanding flash ring
        const currentRadius = progress * RENDER_CONFIG.MUZZLE_FLASH_MAX_RADIUS;
        ctx.beginPath();
        ctx.arc(effect.x, effect.y, Math.max(1, currentRadius), 0, Math.PI * 2);
        ctx.strokeStyle = `rgba(255, 230, 100, ${opacity.toFixed(3)})`;
        ctx.lineWidth = 2 * (1 - progress);
        ctx.stroke();

        // Inner bright spark
        ctx.beginPath();
        ctx.arc(effect.x, effect.y, Math.max(1, (1 - progress) * 6), 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${opacity.toFixed(3)})`;
        ctx.fill();
      }

      ctx.restore();
    }

    this.visualEffects = activeEffects;
  }

  /**
   * Top-level frame rendering method.
   * Sequentially clears viewport, draws arena boundaries/grid, active visual effects,
   * remote players, extrapolated bullets, and renders the local player entity on top.
   *
   * @param playerX              - Current local player horizontal coordinate in pixels.
   * @param playerY              - Current local player vertical coordinate in pixels.
   * @param aimAngle             - Optional current local player aim angle in radians.
   * @param remoteEntities       - Optional array of active remote player entities.
   * @param bullets              - Optional array of active projectiles.
   * @param extrapolationSeconds - Elapsed delta time in seconds since snapshot for bullet extrapolation.
   */
  public render(
    playerX: number = ARENA_W / 2,
    playerY: number = ARENA_H / 2,
    aimAngle?: number,
    remoteEntities: EntityState[] = [],
    bullets: BulletState[] = [],
    extrapolationSeconds: number = 0
  ): void {
    this.clear();
    this.drawArena();
    this.drawEffects();
    this.drawBullets(bullets, extrapolationSeconds);
    this.drawRemotePlayers(remoteEntities);
    this.drawPlayer(
      playerX,
      playerY,
      PLAYER_RADIUS,
      RENDER_CONFIG.PLAYER_LOCAL_COLOR,
      RENDER_CONFIG.PLAYER_OUTLINE_COLOR,
      aimAngle
    );
  }
}
