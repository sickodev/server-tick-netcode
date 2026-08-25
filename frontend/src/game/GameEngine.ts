import { ARENA_W, ARENA_H } from './constants';
import { Renderer } from './Renderer';

/**
 * Core client-side game engine managing the rendering and simulation heartbeat.
 *
 * Bootstraps and drives the browser's `requestAnimationFrame` loop, orchestrates
 * delta-time tracking across frames, and invokes subsystem renders.
 */
export class GameEngine {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private renderer: Renderer;

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

  /**
   * Constructs the GameEngine and initializes the 2D rendering pipeline.
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
   * Stops the game loop and cancels any pending animation frame request.
   */
  public stop(): void {
    this.isRunning = false;
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
  }

  /**
   * Per-frame heartbeat function invoked on every browser repaint cycle.
   * Computes frame delta time and delegates scene rendering to the Renderer.
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

    // Guard against large delta spikes on tab unfocus or lag pauses
    const dt = Math.min(deltaMs / 1000, 0.1);

    // Update and render frame
    this.update(dt);
    this.render();

    // Schedule the next frame
    this.animationFrameId = requestAnimationFrame(this.loop.bind(this));
  }

  /**
   * Updates local game state for the current frame step.
   *
   * @param _dt - Elapsed delta time in seconds since the last frame.
   */
  private update(_dt: number): void {
    // Stationary state for STORY-F01 and STORY-F02.
    // Local player remains fixed at arena center until input handler is wired in F03.
  }

  /**
   * Renders the current frame by passing active entity positions to the Renderer.
   */
  private render(): void {
    this.renderer.render(this.playerX, this.playerY);
  }

  /**
   * Retrieves the Renderer instance.
   *
   * @returns The active Renderer instance.
   */
  public getRenderer(): Renderer {
    return this.renderer;
  }
}
