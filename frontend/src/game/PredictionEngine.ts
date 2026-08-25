import { ARENA_H, ARENA_W, PLAYER_RADIUS, PLAYER_SPEED, TICK_DURATION } from './constants';
import { EntityState, UserCmd } from './types';

/**
 * Maximum number of unacknowledged user commands retained in the circular prediction buffer.
 * Sized to accommodate ~2 seconds of network round-trip time at 64 Hz tick rate.
 */
export const MAX_UNACKED_BUFFER_SIZE = 128;

/**
 * Minimum prediction error distance in pixels required to trigger a position reconciliation snap.
 * Discrepancies below this threshold are ignored to avoid sub-pixel visual micro-jitter.
 */
export const RECONCILIATION_ERROR_THRESHOLD = 1.0;

/**
 * Internal record pairing an outbound UserCmd with its simulation delta time.
 */
export interface UnackedCommandEntry {
  /** The user command dispatched to the server */
  cmd: UserCmd;
  /** The frame delta time in seconds used for this command's simulation step */
  dt: number;
}

/**
 * Bernier Client-Side Prediction and Server Reconciliation Engine.
 *
 * Implements the client prediction model from Yahn Bernier's "Latency Compensating
 * Methods in Client/Server In-game Protocol Design and Optimization" (Valve, 2001).
 *
 * Responsibilities:
 * 1. Immediate local simulation (Story F09): Applies user inputs instantly to local player
 *    kinematics without waiting for network round-trip, buffering unACKed commands.
 * 2. Server reconciliation (Story F10): When authoritative server snapshots arrive,
 *    discards acknowledged commands (seq <= ackSeq), snaps to authoritative server state,
 *    and re-simulates all remaining unacknowledged commands to bring the predicted state current.
 */
export class PredictionEngine {
  /** Current predicted horizontal coordinate in arena pixels */
  private predictedX: number;

  /** Current predicted vertical coordinate in arena pixels */
  private predictedY: number;

  /** Current predicted aim direction in radians */
  private predictedAngle: number;

  /** Circular buffer of dispatched user commands awaiting server acknowledgement */
  private unackedBuffer: UnackedCommandEntry[] = [];

  /** Latest acknowledged command sequence number confirmed by the server */
  private lastAckSeq: number = 0;

  /** Last calculated prediction error distance in pixels between client prediction and server state */
  private predictionError: number = 0;

  /** Current hit points pool of the local player entity (0-100) */
  private health: number = 100;

  /** Timestamp in ms when local player was eliminated (0 if alive) */
  private deathTimestamp: number = 0;

  /**
   * Initializes the PredictionEngine at a given starting position.
   *
   * @param initialX     - Initial horizontal spawn position in pixels (defaults to ARENA_W / 2).
   * @param initialY     - Initial vertical spawn position in pixels (defaults to ARENA_H / 2).
   * @param initialAngle - Initial aim angle in radians (defaults to 0).
   */
  constructor(
    initialX: number = ARENA_W / 2,
    initialY: number = ARENA_H / 2,
    initialAngle: number = 0
  ) {
    this.predictedX = initialX;
    this.predictedY = initialY;
    this.predictedAngle = initialAngle;
  }

  /**
   * Applies deterministic kinematic movement and arena boundary clamping to an entity.
   *
   * CRITICAL INVARIANT:
   * This calculation MUST remain mathematically identical to `physics.ApplyMovement` in `service/physics/movement.go`.
   * Any divergence in math, constants, or clamping order will cause persistent reconciliation fighting.
   *
   * Formula:
   *   speed = entity.speed > 0 ? entity.speed : PLAYER_SPEED
   *   x += dx * speed * dt
   *   y += dy * speed * dt
   *   angle = aimAngle
   *   x = clamp(x, PLAYER_RADIUS, ARENA_W - PLAYER_RADIUS)
   *   y = clamp(y, PLAYER_RADIUS, ARENA_H - PLAYER_RADIUS)
   *
   * @param target - Target object possessing x, y coordinates and optional angle/speed.
   * @param cmd    - The UserCmd input containing directional movement dx, dy and aim angle.
   * @param dt     - Delta time in seconds for the simulation tick.
   */
  public static applyMovement(
    target: { x: number; y: number; angle?: number; speed?: number },
    cmd: UserCmd,
    dt: number
  ): void {
    const speed = target.speed && target.speed > 0 ? target.speed : PLAYER_SPEED;

    target.x += cmd.dx * speed * dt;
    target.y += cmd.dy * speed * dt;
    target.angle = cmd.aimAngle;

    target.x = Math.max(PLAYER_RADIUS, Math.min(ARENA_W - PLAYER_RADIUS, target.x));
    target.y = Math.max(PLAYER_RADIUS, Math.min(ARENA_H - PLAYER_RADIUS, target.y));
  }

  /**
   * Simulates one frame/tick of local movement immediately and stores the command in the unACKed buffer.
   *
   * @param cmd - The input command captured for this frame.
   * @param dt  - Elapsed frame delta time in seconds.
   * @returns The updated local predicted position and aim angle.
   */
  public predict(cmd: UserCmd, dt: number): { x: number; y: number; angle: number } {
    // If local player is dead (0 HP), freeze local prediction and ignore movement inputs
    if (this.health <= 0) {
      return {
        x: this.predictedX,
        y: this.predictedY,
        angle: this.predictedAngle,
      };
    }

    // 1. Simulate movement on current local predicted state
    const state = {
      x: this.predictedX,
      y: this.predictedY,
      angle: this.predictedAngle,
    };
    PredictionEngine.applyMovement(state, cmd, dt);

    this.predictedX = state.x;
    this.predictedY = state.y;
    this.predictedAngle = state.angle;

    // 2. Append command and dt to unACKed buffer
    this.unackedBuffer.push({ cmd, dt });

    // 3. Enforce maximum buffer capacity by dropping oldest unACKed entries if buffer overflows
    if (this.unackedBuffer.length > MAX_UNACKED_BUFFER_SIZE) {
      this.unackedBuffer.shift();
    }

    return {
      x: this.predictedX,
      y: this.predictedY,
      angle: this.predictedAngle,
    };
  }

  /**
   * Reconciles client prediction against authoritative server snapshot state.
   *
   * When an authoritative snapshot arrives with ackSeq:
   * 1. Discard all buffered commands with seq <= ackSeq.
   * 2. Replay all remaining unacknowledged commands starting from the authoritative server position.
   * 3. Compute the prediction discrepancy (error distance).
   * 4. If error exceeds RECONCILIATION_ERROR_THRESHOLD, snap predicted position to reconciled position.
   *
   * @param serverEntity - Authoritative server state for the local player entity at ackSeq.
   * @param ackSeq       - Highest UserCmd sequence number acknowledged by the server.
   */
  public reconcile(serverEntity: EntityState, ackSeq: number): void {
    const prevHealth = this.health;
    // Update local health state from authoritative server snapshot
    this.health = serverEntity.health ?? 100;

    // If local player is eliminated (0 HP), snap to server position, lock prediction, and set death timestamp
    if (this.health <= 0) {
      if (prevHealth > 0 || this.deathTimestamp === 0) {
        this.deathTimestamp = Date.now();
      }
      this.predictedX = serverEntity.x;
      this.predictedY = serverEntity.y;
      this.predictedAngle = serverEntity.angle;
      this.unackedBuffer = [];
      this.predictionError = 0;
      return;
    } else {
      this.deathTimestamp = 0;
    }

    // Ignore stale or out-of-order acknowledgements
    if (ackSeq < this.lastAckSeq && this.lastAckSeq > 0) {
      return;
    }
    this.lastAckSeq = ackSeq;

    // Discard all commands that have been acknowledged and simulated by the server
    this.unackedBuffer = this.unackedBuffer.filter((entry) => entry.cmd.seq > ackSeq);

    // Start simulation replay from the server-authoritative state
    const simState = {
      x: serverEntity.x,
      y: serverEntity.y,
      angle: serverEntity.angle,
    };

    // Re-simulate all remaining unacknowledged commands forward to the current local timeline
    for (const entry of this.unackedBuffer) {
      PredictionEngine.applyMovement(simState, entry.cmd, entry.dt || TICK_DURATION / 1000);
    }

    // Calculate prediction error distance between old predicted position and authoritative replay position
    const errorDistance = Math.hypot(this.predictedX - simState.x, this.predictedY - simState.y);
    this.predictionError = errorDistance;

    // If prediction diverged beyond the threshold, snap to the reconciled position
    if (errorDistance >= RECONCILIATION_ERROR_THRESHOLD) {
      this.predictedX = simState.x;
      this.predictedY = simState.y;
      this.predictedAngle = simState.angle;
    }
  }

  /**
   * Retrieves the current predicted local player position coordinates.
   *
   * @returns Object containing x and y coordinates in pixels.
   */
  public getPosition(): { x: number; y: number } {
    return { x: this.predictedX, y: this.predictedY };
  }

  /**
   * Retrieves the current predicted local player aim angle.
   *
   * @returns Aim angle in radians.
   */
  public getAngle(): number {
    return this.predictedAngle;
  }

  /**
   * Sets the predicted position directly, resetting unACKed buffer (used on initial join/spawn).
   *
   * @param x     - Horizontal position in arena pixels.
   * @param y     - Vertical position in arena pixels.
   * @param angle - Optional aim angle in radians.
   */
  public setPosition(x: number, y: number, angle?: number): void {
    this.predictedX = Math.max(PLAYER_RADIUS, Math.min(ARENA_W - PLAYER_RADIUS, x));
    this.predictedY = Math.max(PLAYER_RADIUS, Math.min(ARENA_H - PLAYER_RADIUS, y));
    if (angle !== undefined) {
      this.predictedAngle = angle;
    }
  }

  /**
   * Returns the most recently computed prediction error in pixels.
   * Consumed by the debug overlay HUD to visualize netcode drift.
   *
   * @returns Prediction error distance in pixels.
   */
  public getPredictionError(): number {
    return this.predictionError;
  }

  /**
   * Returns the number of currently buffered unacknowledged commands.
   *
   * @returns Count of unACKed commands awaiting server confirmation.
   */
  public getUnackedCount(): number {
    return this.unackedBuffer.length;
  }

  /**
   * Retrieves an array of all unacknowledged UserCmd objects currently in the buffer.
   *
   * @returns Array of buffered UserCmd instances.
   */
  public getUnackedBuffer(): UserCmd[] {
    return this.unackedBuffer.map((entry) => entry.cmd);
  }

  /**
   * Returns the latest command sequence number acknowledged by the server.
   *
   * @returns Sequence number.
   */
  public getLastAckSeq(): number {
    return this.lastAckSeq;
  }

  /**
   * Returns current health of the local player.
   *
   * @returns Health value (0-100).
   */
  public getHealth(): number {
    return this.health;
  }

  /**
   * Returns remaining respawn countdown duration in seconds if local player is eliminated (0 HP).
   *
   * @returns Remaining time in seconds (0 if alive).
   */
  public getRespawnTimeRemaining(): number {
    if (this.health > 0 || this.deathTimestamp === 0) {
      return 0;
    }
    const elapsedSec = (Date.now() - this.deathTimestamp) / 1000;
    return Math.max(0, 3.0 - elapsedSec);
  }

  /**
   * Resets internal prediction state and purges the unACKed command buffer.
   *
   * @param x     - Optional horizontal reset position in pixels.
   * @param y     - Optional vertical reset position in pixels.
   * @param angle - Optional reset aim angle in radians.
   */
  public reset(
    x: number = ARENA_W / 2,
    y: number = ARENA_H / 2,
    angle: number = 0
  ): void {
    this.predictedX = x;
    this.predictedY = y;
    this.predictedAngle = angle;
    this.unackedBuffer = [];
    this.lastAckSeq = 0;
    this.predictionError = 0;
  }
}
