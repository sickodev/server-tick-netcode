import { ARENA_W, ARENA_H, PLAYER_RADIUS } from './constants';
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
  BULLET_RADIUS: 5,
  /** Text color for player identification labels */
  PLAYER_LABEL_COLOR: '#ffffff',
  /** Typography style for player identification labels */
  PLAYER_LABEL_FONT: '11px monospace',
} as const;

/**
 * 2D Canvas rendering engine responsible for painting the game arena, local player,
 * remote players, projectiles, and visual indicators.
 *
 * Owns all direct HTML5 Canvas 2D context drawing operations.
 */
export class Renderer {
  private ctx: CanvasRenderingContext2D;

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
    // Fill the full canvas area with dark background tone
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
      // Display truncated identifier or label above remote player
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
   * Renders all active projectiles currently in flight within the arena.
   *
   * @param bullets - Array of BulletState objects to draw.
   */
  public drawBullets(bullets: BulletState[]): void {
    const { ctx } = this;

    ctx.save();
    ctx.fillStyle = RENDER_CONFIG.BULLET_COLOR;

    for (const bullet of bullets) {
      ctx.beginPath();
      ctx.arc(bullet.x, bullet.y, RENDER_CONFIG.BULLET_RADIUS, 0, Math.PI * 2, false);
      ctx.fill();
    }

    ctx.restore();
  }

  /**
   * Top-level frame rendering method.
   * Sequentially clears viewport, draws arena boundaries/grid, remote players, bullets,
   * and renders the local player entity on top.
   *
   * @param playerX        - Current local player horizontal coordinate in pixels.
   * @param playerY        - Current local player vertical coordinate in pixels.
   * @param aimAngle       - Optional current local player aim angle in radians.
   * @param remoteEntities - Optional array of active remote player entities.
   * @param bullets        - Optional array of active projectiles.
   */
  public render(
    playerX: number = ARENA_W / 2,
    playerY: number = ARENA_H / 2,
    aimAngle?: number,
    remoteEntities: EntityState[] = [],
    bullets: BulletState[] = []
  ): void {
    this.clear();
    this.drawArena();
    this.drawBullets(bullets);
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
