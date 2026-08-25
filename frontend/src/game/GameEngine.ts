import { ARENA_W, ARENA_H, PLAYER_RADIUS, PLAYER_SPEED } from './constants';
import { InputHandler } from './InputHandler';
import { NetworkClient } from './NetworkClient';
import { Renderer } from './Renderer';
import { BulletState, EntityState, Snapshot, UserCmd } from './types';

/**
 * Core client-side game engine managing the rendering, input sampling, and simulation heartbeat.
 *
 * Bootstraps and drives the browser's `requestAnimationFrame` loop, orchestrates
 * delta-time tracking across frames, captures player input via InputHandler, dispatches
 * high-frequency UserCmd packets over WebSocket via NetworkClient, applies authoritative
 * snapshot updates from the server, and invokes subsystem renders.
 */
export class GameEngine {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private renderer: Renderer;
  private inputHandler: InputHandler;
  private networkClient: NetworkClient;

  /** Identifier of the active requestAnimationFrame callback, or null if stopped */
  private animationFrameId: number | null = null;

  /** Timestamp in milliseconds of the previous frame invocation */
  private lastFrameTime: number = 0;

  /** Flag indicating whether the main game loop is currently executing */
  private isRunning: boolean = false;

  /** Monotonically increasing sequence counter for outbound UserCmd packets */
  private seq: number = 1;

  /** Local player horizontal coordinate in pixels */
  private playerX: number = ARENA_W / 2;

  /** Local player vertical coordinate in pixels */
  private playerY: number = ARENA_H / 2;

  /** Local player aim direction in radians (facing mouse cursor) */
  private aimAngle: number = 0;

  /** Array of active remote player entities received from latest server snapshot */
  private remoteEntities: EntityState[] = [];

  /** Array of active projectile bullets received from latest server snapshot */
  private bullets: BulletState[] = [];

  /** Unsubscribe callback handle for snapshot listener */
  private unsubscribeSnapshot: (() => void) | null = null;

  /**
   * Constructs the GameEngine and initializes the 2D rendering pipeline, input handler,
   * and network client.
   *
   * @param canvas        - The HTML5 canvas DOM element on which the game is drawn.
   * @param networkClient - Optional NetworkClient instance override.
   * @throws Error if the 2D rendering context cannot be retrieved.
   */
  constructor(canvas: HTMLCanvasElement, networkClient?: NetworkClient) {
    this.canvas = canvas;
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('[GameEngine] Failed to acquire 2D rendering context from canvas element.');
    }
    this.ctx = context;
    this.renderer = new Renderer(this.ctx);
    this.inputHandler = new InputHandler(this.canvas);
    this.networkClient = networkClient || new NetworkClient();
  }

  /**
   * Starts the game loop using requestAnimationFrame and establishes network connection.
   * Does nothing if the engine is already running.
   */
  public start(): void {
    if (this.isRunning) {
      return;
    }
    this.isRunning = true;
    this.lastFrameTime = performance.now();

    // Subscribe to incoming authoritative server snapshots
    this.unsubscribeSnapshot = this.networkClient.onSnapshot(this.handleSnapshot.bind(this));

    // Connect to WebSocket gateway
    this.networkClient.connect();

    // Begin frame loop
    this.animationFrameId = requestAnimationFrame(this.loop.bind(this));
  }

  /**
   * Stops the game loop, disconnects from the gateway, and cleans up event listeners.
   */
  public stop(): void {
    this.isRunning = false;
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }

    if (this.unsubscribeSnapshot) {
      this.unsubscribeSnapshot();
      this.unsubscribeSnapshot = null;
    }

    this.networkClient.disconnect();
    this.inputHandler.cleanup();
  }

  /**
   * Handles authoritative world snapshot received from the server.
   * Updates local player position directly (F08 snapshot snapping) and stores remote entities and bullets.
   *
   * @param snapshot - The authoritative world snapshot from server tick.
   */
  private handleSnapshot(snapshot: Snapshot): void {
    const myId = this.networkClient.getPlayerId();

    // Identify self entity from snapshot payload
    const selfEntity = snapshot.entities.find(
      (entity) => entity.isSelf === true || entity.is_self === true || entity.id === myId
    );

    if (selfEntity) {
      // In F08 (pre-prediction), snap local player directly to authoritative server state
      this.playerX = selfEntity.x;
      this.playerY = selfEntity.y;
      this.aimAngle = selfEntity.angle;
    }

    // Filter remote player entities (all active players except self)
    const selfId = selfEntity?.id ?? myId;
    this.remoteEntities = snapshot.entities.filter(
      (entity) => entity !== selfEntity && entity.id !== selfId && entity.isSelf !== true && entity.is_self !== true
    );

    // Update active projectiles
    this.bullets = snapshot.bullets || [];
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

    // Advance local state simulation, dispatch UserCmd over network, and render frame
    this.update(dt);
    this.render();

    // Schedule the next frame
    this.animationFrameId = requestAnimationFrame(this.loop.bind(this));
  }

  /**
   * Updates local game state for the current frame step.
   * Reads input, advances player translation, calculates aim angle, and transmits UserCmd.
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

    // 5. Read fire trigger state
    const fire = this.inputHandler.isFiring();

    // 6. Build and dispatch frame UserCmd packet
    const userCmd: UserCmd = {
      seq: this.seq++,
      timestamp: Date.now(),
      dx,
      dy,
      aimAngle: this.aimAngle,
      fire,
    };

    this.networkClient.sendUserCmd(userCmd);
  }

  /**
   * Renders the current frame by passing active entity positions, remote entities,
   * and projectiles to the Renderer.
   */
  private render(): void {
    this.renderer.render(
      this.playerX,
      this.playerY,
      this.aimAngle,
      this.remoteEntities,
      this.bullets
    );
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
   * Retrieves the active NetworkClient instance.
   *
   * @returns The NetworkClient instance.
   */
  public getNetworkClient(): NetworkClient {
    return this.networkClient;
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
