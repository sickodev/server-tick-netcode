import { ARENA_W, ARENA_H, PLAYER_RADIUS } from './constants';

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
  /** Local player outer stroke color */
  PLAYER_OUTLINE_COLOR: '#ffffff',
  /** Local player outer stroke width in pixels */
  PLAYER_OUTLINE_WIDTH: 2,
  /** Directional aim line stroke color */
  AIM_LINE_COLOR: '#ffffff',
  /** Directional aim line length extending from player center in pixels */
  AIM_LINE_LENGTH: 30,
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
   */
  public drawPlayer(
    x: number,
    y: number,
    radius: number = PLAYER_RADIUS,
    fillColor: string = RENDER_CONFIG.PLAYER_LOCAL_COLOR,
    outlineColor: string = RENDER_CONFIG.PLAYER_OUTLINE_COLOR,
    aimAngle?: number
  ): void {
    const { ctx } = this;

    // Draw player body circle
    ctx.save();
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

    ctx.restore();
  }

  /**
   * Top-level frame rendering method.
   * Sequentially clears viewport, draws arena boundaries/grid, and renders the player entity.
   *
   * @param playerX  - Current local player horizontal coordinate in pixels.
   * @param playerY  - Current local player vertical coordinate in pixels.
   * @param aimAngle - Optional current local player aim angle in radians.
   */
  public render(
    playerX: number = ARENA_W / 2,
    playerY: number = ARENA_H / 2,
    aimAngle?: number
  ): void {
    this.clear();
    this.drawArena();
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
