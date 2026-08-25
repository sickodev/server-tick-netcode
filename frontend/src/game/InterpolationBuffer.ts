import { EntityState, Snapshot } from './types';

/**
 * Default entity interpolation render delay in milliseconds.
 * Remote players are rendered 100ms in the past to guarantee a smooth continuous
 * stream of bracketing authoritative snapshots, eliminating stutter and packet jitter.
 */
export const DEFAULT_INTERP_DELAY_MS = 100;

/**
 * Maximum number of timestamped snapshots retained in the interpolation ring buffer.
 * At 64 Hz tick rate, 32 snapshots provide ~500ms of historical buffer window.
 */
export const MAX_SNAPSHOT_HISTORY = 32;

/**
 * Represents an authoritative world snapshot paired with the local client reception timestamp.
 */
export interface TimestampedSnapshot {
  /** Local client epoch timestamp in milliseconds when this snapshot was received */
  timestamp: number;
  /** Authoritative server simulation tick index */
  serverTick: number;
  /** Collection of player entity states captured in this snapshot */
  entities: EntityState[];
}

/**
 * Entity Interpolation Buffer for Remote Players.
 *
 * Implements the remote entity interpolation technique described in Bernier (2001).
 * Remote player entities are delayed by a fixed buffer window (INTERP_DELAY_MS = 100ms)
 * and rendered by linearly interpolating (LERP) position coordinates and aim angles
 * between the two surrounding authoritative server snapshots S0 and S1.
 *
 * This guarantees smooth visual motion even under fluctuating network latency or packet jitter.
 */
export class InterpolationBuffer {
  /** Ordered historical buffer of timestamped server snapshots */
  private snapshots: TimestampedSnapshot[] = [];

  /** Active interpolation delay in milliseconds */
  private interpDelayMs: number;

  /**
   * Initializes the InterpolationBuffer with a configurable delay.
   *
   * @param interpDelayMs - Render delay in milliseconds (defaults to DEFAULT_INTERP_DELAY_MS = 100).
   */
  constructor(interpDelayMs: number = DEFAULT_INTERP_DELAY_MS) {
    this.interpDelayMs = interpDelayMs;
  }

  /**
   * Ingests a new authoritative server snapshot and timestamps it with the client reception time.
   *
   * @param snapshot    - Authoritative server snapshot payload.
   * @param receiveTime - Optional explicit reception timestamp in milliseconds (defaults to Date.now()).
   */
  public addSnapshot(snapshot: Snapshot, receiveTime: number = Date.now()): void {
    const serverTick = snapshot.serverTick ?? snapshot.server_tick ?? 0;
    const entities = snapshot.entities || [];

    const entry: TimestampedSnapshot = {
      timestamp: receiveTime,
      serverTick,
      entities: entities.map((e) => ({ ...e })),
    };

    // Maintain chronological order in case of out-of-order network arrival
    if (this.snapshots.length === 0 || receiveTime >= this.snapshots[this.snapshots.length - 1].timestamp) {
      this.snapshots.push(entry);
    } else {
      // Find insertion position
      let insertIdx = 0;
      while (insertIdx < this.snapshots.length && this.snapshots[insertIdx].timestamp <= receiveTime) {
        insertIdx++;
      }
      this.snapshots.splice(insertIdx, 0, entry);
    }

    // Enforce fixed ring buffer capacity by purging oldest snapshots
    while (this.snapshots.length > MAX_SNAPSHOT_HISTORY) {
      this.snapshots.shift();
    }
  }

  /**
   * Computes the shortest angular distance and interpolates between two angles in radians.
   * Properly wraps around the circle (-PI to PI) to prevent 359° reverse spinning artifacts.
   *
   * @param a0    - Source starting angle in radians.
   * @param a1    - Target ending angle in radians.
   * @param alpha - Interpolation weighting factor between 0.0 and 1.0.
   * @returns Smoothly interpolated angle in radians.
   */
  public static interpolateAngle(a0: number, a1: number, alpha: number): number {
    let diff = (a1 - a0) % (Math.PI * 2);
    if (diff > Math.PI) {
      diff -= Math.PI * 2;
    } else if (diff < -Math.PI) {
      diff += Math.PI * 2;
    }
    return a0 + diff * alpha;
  }

  /**
   * Evaluates and returns the interpolated positions and angles for all remote entities at renderTime.
   *
   * Finds two bracketing snapshots S0 and S1 such that:
   *   S0.timestamp <= renderTime <= S1.timestamp
   * and computes position via LERP:
   *   x = (1 - alpha) * s0.x + alpha * s1.x
   *   y = (1 - alpha) * s0.y + alpha * s1.y
   *
   * @param explicitRenderTime - Optional target playback timestamp in milliseconds.
   *                             If omitted, computes `Date.now() - interpDelayMs`.
   * @returns Array of smoothly interpolated EntityState records for rendering.
   */
  public getInterpolatedEntities(explicitRenderTime?: number): EntityState[] {
    if (this.snapshots.length === 0) {
      return [];
    }

    // Compute target historical playback time
    const renderTime = explicitRenderTime ?? Date.now() - this.interpDelayMs;

    // Edge Case 1: Only a single snapshot is available in buffer (initial connect)
    if (this.snapshots.length === 1) {
      return this.snapshots[0].entities;
    }

    const oldest = this.snapshots[0];
    const newest = this.snapshots[this.snapshots.length - 1];

    // Edge Case 2: Target render time is older than the oldest snapshot in buffer
    if (renderTime <= oldest.timestamp) {
      return oldest.entities;
    }

    // Edge Case 3: Target render time is newer than newest snapshot (buffer underrun / packet stall)
    if (renderTime >= newest.timestamp) {
      return newest.entities;
    }

    // Find the two surrounding snapshots S0 and S1 that bracket renderTime
    let s0: TimestampedSnapshot = oldest;
    let s1: TimestampedSnapshot = newest;

    for (let i = 0; i < this.snapshots.length - 1; i++) {
      if (this.snapshots[i].timestamp <= renderTime && renderTime <= this.snapshots[i + 1].timestamp) {
        s0 = this.snapshots[i];
        s1 = this.snapshots[i + 1];
        break;
      }
    }

    // Compute fractional interpolation progress factor alpha in range [0, 1]
    const timeSpan = s1.timestamp - s0.timestamp;
    const rawAlpha = timeSpan > 0 ? (renderTime - s0.timestamp) / timeSpan : 0;
    const alpha = Math.max(0, Math.min(1, rawAlpha));

    // Build entity map for fast lookup of S0 entity matching S1 entity
    const s0Map = new Map<string, EntityState>();
    for (const entity of s0.entities) {
      s0Map.set(entity.id, entity);
    }

    const interpolatedList: EntityState[] = [];

    // Interpolate all entities present in the target snapshot S1
    for (const s1Entity of s1.entities) {
      const s0Entity = s0Map.get(s1Entity.id);

      if (s0Entity) {
        // Linearly interpolate positions: x = (1 - a)*x0 + a*x1
        const interpolatedX = (1 - alpha) * s0Entity.x + alpha * s1Entity.x;
        const interpolatedY = (1 - alpha) * s0Entity.y + alpha * s1Entity.y;
        const interpolatedAngle = InterpolationBuffer.interpolateAngle(
          s0Entity.angle,
          s1Entity.angle,
          alpha
        );

        interpolatedList.push({
          id: s1Entity.id,
          x: interpolatedX,
          y: interpolatedY,
          angle: interpolatedAngle,
          health: s1Entity.health,
          isSelf: s1Entity.isSelf,
          is_self: s1Entity.is_self,
        });
      } else {
        // Entity just spawned in S1 and was absent in S0 — render directly at S1 position
        interpolatedList.push({ ...s1Entity });
      }
    }

    return interpolatedList;
  }

  /**
   * Retrieves the current interpolation delay in milliseconds.
   *
   * @returns Active delay duration in ms.
   */
  public getInterpDelayMs(): number {
    return this.interpDelayMs;
  }

  /**
   * Updates the interpolation delay setting.
   *
   * @param delayMs - New render delay in milliseconds.
   */
  public setInterpDelayMs(delayMs: number): void {
    this.interpDelayMs = Math.max(0, delayMs);
  }

  /**
   * Returns the count of snapshots currently stored in the history buffer.
   *
   * @returns Number of retained snapshots.
   */
  public getSnapshotCount(): number {
    return this.snapshots.length;
  }

  /**
   * Purges all snapshots from the buffer.
   */
  public clear(): void {
    this.snapshots = [];
  }
}
