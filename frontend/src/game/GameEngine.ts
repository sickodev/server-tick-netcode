import { ARENA_W, ARENA_H, PLAYER_RADIUS, PLAYER_SPEED } from './constants';
import { InputHandler } from './InputHandler';
import { Renderer } from './Renderer';

/**
 * Core client-side game engine managing the rendering and simulation heartbeat.
 *
 * Bootstraps and drives the browser's `requestAnimationFrame` loop, orchestrates
 * delta-time tracking across frames, captures player input via InputHandler,
 * updates local player physics, and invokes subsystem renders.
 */
export class GameEngine {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private renderer: Renderer;
  private inputHandler: InputHandler;

  /** Identifier of the active requestAnimationFrame callback, or null if stopped */
  private animationFrameId: number | null = null;

  /** Timestamp in milliseconds of the previous frame invocation */
  private lastFrameTime: number = 0;

  /** Flag indicating whether the main game loop is currently executing */
  private isRunning: boolean = false;

  /** Local player horizontal coordinate in pixels */
  private playerX: number = ARENA_W / 2;

  /** Local player vertical coordinate in pixels */
  private playerY: number = ARENA_H / 2;

  /** Local player aim direction in radians (facing mouse cursor) */
  private aimAngle: number = 0;

  /**
   * Constructs the GameEngine and initializes the 2D rendering pipeline and input handler.
   *
   * @param canvas - The HTML5 canvas DOM element on which the game is drawn.
   * @throws Error if the 2D rendering context cannot be retrieved.
   */
  constructor(canvas: HTMLCanvasElement) {
    this.canvas = canvas;
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('[GameEngine] Failed to acquire 2D rendering context from canvas element.');
    }
    this.ctx = context;
    this.renderer = new Renderer(this.ctx);
    this.inputHandler = new InputHandler(this.canvas);
  }

  /**
   * Starts the game loop using requestAnimationFrame.
   * Does nothing if the engine is already running.
   */
  public start(): void {
    if (this.isRunning) {
      return;
    }
    this.isRunning = true;
    this.lastFrameTime = performance.now();
    this.animationFrameId = requestAnimationFrame(this.loop.bind(this));
  }

  /**
   * Stops the game loop and cleans up event listeners and animation frames.
   */
  public stop(): void {
    this.isRunning = false;
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
    this.inputHandler.cleanup();
  }

  /**
   * Per-frame heartbeat function invoked on every browser repaint cycle.
   * Computes frame delta time and delegates simulation updates and scene rendering.
   *
   * @param currentTime - High-resolution timestamp passed by requestAnimationFrame.
   */
  private loop(currentTime: number): void {
    if (!this.isRunning) {
      return;
    }

    // Compute elapsed time since the previous frame in seconds
    const deltaMs = currentTime - this.lastFrameTime;
    this.lastFrameTime = currentTime;

    // Guard against large delta spikes on tab unfocus or lag pauses (cap at 100ms)
    const dt = Math.min(deltaMs / 1000, 0.1);

    // Advance local state simulation and render frame
    this.update(dt);
    this.render();

    // Schedule the next frame
    this.animationFrameId = requestAnimationFrame(this.loop.bind(this));
  }

  /**
   * Updates local game state for the current frame step.
   * Moves player according to WASD input with deltaTime scaling and clamps to arena boundaries.
   *
   * @param dt - Elapsed delta time in seconds since the last frame.
   */
  private update(dt: number): void {
    // 1. Read directional movement vector from user input
    const { dx, dy } = this.inputHandler.getMovementVector();

    // 2. Apply frame-rate independent position translation
    this.playerX += dx * PLAYER_SPEED * dt;
    this.playerY += dy * PLAYER_SPEED * dt;

    // 3. Clamp player position to arena boundaries with PLAYER_RADIUS inset
    this.playerX = Math.max(PLAYER_RADIUS, Math.min(ARENA_W - PLAYER_RADIUS, this.playerX));
    this.playerY = Math.max(PLAYER_RADIUS, Math.min(ARENA_H - PLAYER_RADIUS, this.playerY));

    // 4. Update aim angle pointing from current player position to mouse cursor
    this.aimAngle = this.inputHandler.getAimAngle(this.playerX, this.playerY);
  }

  /**
   * Renders the current frame by passing active entity positions to the Renderer.
   */
  private render(): void {
    this.renderer.render(this.playerX, this.playerY, this.aimAngle);
  }

  /**
   * Retrieves the active Renderer instance.
   *
   * @returns The Renderer instance.
   */
  public getRenderer(): Renderer {
    return this.renderer;
  }

  /**
   * Retrieves the active InputHandler instance.
   *
   * @returns The InputHandler instance.
   */
  public getInputHandler(): InputHandler {
    return this.inputHandler;
  }

  /**
   * Retrieves the current local player position coordinates.
   *
   * @returns Object containing x and y coordinates in pixels.
   */
  public getPlayerPosition(): { x: number; y: number } {
    return { x: this.playerX, y: this.playerY };
  }

  /**
   * Retrieves the current local player aim direction in radians.
   *
   * @returns Aim angle in radians.
   */
  public getAimAngle(): number {
    return this.aimAngle;
  }
}
