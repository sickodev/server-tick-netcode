import { InputHandler } from './InputHandler';
import { InterpolationBuffer } from './InterpolationBuffer';
import { NetworkClient } from './NetworkClient';
import { PredictionEngine } from './PredictionEngine';
import { Renderer } from './Renderer';
import { BulletState, Snapshot, UserCmd } from './types';

/**
 * Core client-side game engine orchestrating the input capture, prediction,
 * network replication, entity interpolation, and 2D canvas rendering loop.
 *
 * Sequence on each frame (Bernier 2001 latency compensation architecture):
 * 1. Capture user inputs (WASD movement, aim angle, firing) via InputHandler.
 * 2. Immediately simulate local player kinematics via PredictionEngine (Story F09).
 * 3. Transmit UserCmd packet containing sequence number and inputs via NetworkClient.
 * 4. Retrieve smoothly interpolated positions for all remote players via InterpolationBuffer (Story F11).
 * 5. Reconcile local player prediction against authoritative server state when snapshots arrive (Story F10).
 * 6. Extrapolate active projectile trajectories and render complete scene via Renderer (Story F12).
 */
export class GameEngine {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private renderer: Renderer;
  private inputHandler: InputHandler;
  private networkClient: NetworkClient;
  private predictionEngine: PredictionEngine;
  private interpBuffer: InterpolationBuffer;

  /** Identifier of the active requestAnimationFrame callback, or null if stopped */
  private animationFrameId: number | null = null;

  /** Timestamp in milliseconds of the previous frame invocation */
  private lastFrameTime: number = 0;

  /** Flag indicating whether the main game loop is currently executing */
  private isRunning: boolean = false;

  /** Monotonically increasing sequence counter for outbound UserCmd packets */
  private seq: number = 1;

  /** Array of active projectile bullets received from latest server snapshot */
  private bullets: BulletState[] = [];

  /** Local client epoch timestamp in milliseconds when the latest snapshot was received */
  private lastSnapshotTime: number = Date.now();

  /** Unsubscribe callback handle for snapshot listener */
  private unsubscribeSnapshot: (() => void) | null = null;

  /**
   * Constructs the GameEngine and initializes the rendering context, input handler,
   * prediction engine, interpolation buffer, and network client.
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
    this.predictionEngine = new PredictionEngine();
    this.interpBuffer = new InterpolationBuffer();
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
   * Feeds snapshot into the remote entity interpolation buffer and triggers
   * local player server reconciliation against the acknowledged command sequence.
   *
   * @param snapshot - The authoritative world snapshot from server tick.
   */
  private handleSnapshot(snapshot: Snapshot): void {
    const receiveTime = Date.now();
    this.lastSnapshotTime = receiveTime;

    // Ingest snapshot into InterpolationBuffer for remote player smoothing
    this.interpBuffer.addSnapshot(snapshot, receiveTime);

    const myId = this.networkClient.getPlayerId();

    // Locate local player state in snapshot payload
    const selfEntity = snapshot.entities.find(
      (entity) => entity.isSelf === true || entity.is_self === true || entity.id === myId
    );

    // Reconcile prediction with server authoritative position
    if (selfEntity) {
      const ackSeq = snapshot.ackSeq ?? snapshot.ack_seq ?? 0;
      this.predictionEngine.reconcile(selfEntity, ackSeq);
    }

    // Cache active projectile bullets
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
   * Reads input, performs client-side prediction, triggers visual effects, and transmits UserCmd.
   *
   * @param dt - Elapsed delta time in seconds since the last frame.
   */
  private update(dt: number): void {
    // 1. Query predicted position to calculate mouse aim angle
    const currentPredictedPos = this.predictionEngine.getPosition();

    // 2. Read directional movement vector and aim angle from user input
    const { dx, dy } = this.inputHandler.getMovementVector();
    const aimAngle = this.inputHandler.getAimAngle(currentPredictedPos.x, currentPredictedPos.y);

    // 3. Read fire trigger state
    const fire = this.inputHandler.isFiring();

    // 4. Build frame UserCmd packet
    const userCmd: UserCmd = {
      seq: this.seq++,
      timestamp: Date.now(),
      dx,
      dy,
      aimAngle,
      fire,
    };

    // 5. Client-Side Prediction (Story F09): Simulate local movement immediately
    const predictedPos = this.predictionEngine.predict(userCmd, dt);

    // 6. Story F12: Instant visual feedback on weapon fire (no waiting for server)
    if (fire) {
      this.renderer.addMuzzleFlash(predictedPos.x, predictedPos.y, aimAngle);
    }

    // 7. Transmit command packet to authoritative server
    this.networkClient.sendUserCmd(userCmd);
  }

  /**
   * Renders the current frame by drawing predicted local player, interpolated
   * remote players, extrapolated projectiles, and particle visual effects.
   */
  private render(): void {
    // 1. Retrieve local player predicted coordinates, aim angle, and health
    const localPos = this.predictionEngine.getPosition();
    const localAngle = this.predictionEngine.getAngle();
    const localHealth = this.predictionEngine.getHealth();

    // 2. Story F11: Retrieve smoothly interpolated remote player entities
    const interpolatedEntities = this.interpBuffer.getInterpolatedEntities();
    const myId = this.networkClient.getPlayerId();

    // Filter out local player entity from remote entity rendering
    const remoteEntities = interpolatedEntities.filter(
      (entity) => entity.id !== myId && entity.isSelf !== true && entity.is_self !== true
    );

    // 3. Story F12: Calculate projectile extrapolation time offset in seconds
    const elapsedSec = (Date.now() - this.lastSnapshotTime) / 1000;
    const extrapolationSeconds = Math.max(0, Math.min(elapsedSec, 0.25));

    // 4. Paint scene
    this.renderer.render(
      localPos.x,
      localPos.y,
      localAngle,
      remoteEntities,
      this.bullets,
      extrapolationSeconds,
      localHealth
    );
  }

  /**
   * Retrieves the active PredictionEngine instance.
   * Consumed by debug HUD overlay to inspect prediction error and unACKed queue size.
   *
   * @returns The PredictionEngine instance.
   */
  public getPredictionEngine(): PredictionEngine {
    return this.predictionEngine;
  }

  /**
   * Retrieves the active InterpolationBuffer instance.
   * Consumed by debug HUD overlay to inspect snapshot count and interp delay.
   *
   * @returns The InterpolationBuffer instance.
   */
  public getInterpolationBuffer(): InterpolationBuffer {
    return this.interpBuffer;
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
   * Retrieves the current local predicted player position coordinates.
   *
   * @returns Object containing x and y coordinates in pixels.
   */
  public getPlayerPosition(): { x: number; y: number } {
    return this.predictionEngine.getPosition();
  }

  /**
   * Retrieves the current local player aim direction in radians.
   *
   * @returns Aim angle in radians.
   */
  public getAimAngle(): number {
    return this.predictionEngine.getAngle();
  }
}
