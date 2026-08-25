/**
 * @fileoverview Shared TypeScript type definitions for the game netcode client,
 * WebSocket protocol message envelopes, entity models, and snapshot replication payloads.
 */

/**
 * Authoritative user command payload captured for a single frame or simulation tick.
 * Sent by the frontend over WebSocket to drive authoritative movement and weapon firing.
 */
export interface UserCmd {
  /** Monotonically increasing sequence number for this command */
  seq: number;
  /** Client-side timestamp in milliseconds when input was captured */
  timestamp: number;
  /** Normalized horizontal movement delta (-1 to 1) */
  dx: number;
  /** Normalized vertical movement delta (-1 to 1) */
  dy: number;
  /** Aim direction angle in radians (facing direction toward mouse cursor) */
  aimAngle: number;
  /** Whether the primary fire action was triggered in this frame */
  fire: boolean;
}

/**
 * Outgoing client message requesting entry into the multiplayer game arena.
 */
export interface JoinMessage {
  /** Protocol message discriminator, always "join" */
  type: 'join';
  /** Unique player identifier generated for the lifetime of the client session */
  playerId: string;
  /** Human-readable display name chosen by the player */
  name: string;
}

/**
 * Outgoing client message transmitting a discrete input command frame to the gateway.
 */
export interface UserCmdMessage {
  /** Protocol message discriminator, always "user_cmd" */
  type: 'user_cmd';
  /** Monotonically increasing sequence number for prediction acknowledgement */
  seq: number;
  /** Client timestamp in milliseconds when input was sampled */
  timestamp: number;
  /** Normalized horizontal movement vector (-1 to 1) */
  dx: number;
  /** Normalized vertical movement vector (-1 to 1) */
  dy: number;
  /** Aim direction angle in radians (supports camelCase) */
  aimAngle: number;
  /** Aim direction angle in radians (supports snake_case compatibility) */
  aim_angle?: number;
  /** Whether the fire trigger was pressed during this command tick */
  fire: boolean;
}

/**
 * Union type representing all valid JSON payloads sent from the client to the gateway.
 */
export type ClientMessage = JoinMessage | UserCmdMessage;

/**
 * Incoming server message acknowledging a successful player join request.
 */
export interface JoinResponseMessage {
  /** Protocol message discriminator, always "joinResponse" */
  type: 'joinResponse';
  /** Whether admission into the game arena was granted */
  ok: boolean;
  /** Initial horizontal spawn position in pixels */
  spawnX?: number;
  /** Initial vertical spawn position in pixels */
  spawnY?: number;
  /** Initial horizontal spawn position (snake_case alias) */
  spawn_x?: number;
  /** Initial vertical spawn position (snake_case alias) */
  spawn_y?: number;
}

/**
 * Authoritative state representation of a single player entity within the arena.
 */
export interface EntityState {
  /** Unique identifier corresponding to the player's session or player ID */
  id: string;
  /** Authoritative horizontal position in arena pixels */
  x: number;
  /** Authoritative vertical position in arena pixels */
  y: number;
  /** Current aim / facing angle in radians */
  angle: number;
  /** Current health points (0 represents an eliminated player) */
  health: number;
  /** Flag indicating if this entity represents the local client player (camelCase) */
  isSelf?: boolean;
  /** Flag indicating if this entity represents the local client player (snake_case) */
  is_self?: boolean;
}

/**
 * Authoritative state representation of an active bullet projectile in flight.
 */
export interface BulletState {
  /** Unique projectile identifier */
  id: string;
  /** Identifier of the player who fired this projectile (camelCase) */
  ownerId?: string;
  /** Identifier of the player who fired this projectile (snake_case) */
  owner_id?: string;
  /** Current horizontal position in arena pixels */
  x: number;
  /** Current vertical position in arena pixels */
  y: number;
  /** Horizontal velocity component in pixels per second */
  vx: number;
  /** Vertical velocity component in pixels per second */
  vy: number;
}

/**
 * Authoritative periodic tick snapshot broadcast from the server.
 * Contains global world state for player entity positions and active projectiles.
 */
export interface Snapshot {
  /** Protocol message discriminator, always "snapshot" */
  type?: 'snapshot';
  /** Monotonically increasing server simulation tick counter (camelCase) */
  serverTick?: number;
  /** Monotonically increasing server simulation tick counter (snake_case) */
  server_tick?: number;
  /** Highest UserCmd sequence number acknowledged by the server for the local player */
  ackSeq?: number;
  /** Highest UserCmd sequence number acknowledged (snake_case alias) */
  ack_seq?: number;
  /** List of all active player entities currently present in the arena */
  entities: EntityState[];
  /** List of all active projectiles currently in flight */
  bullets: BulletState[];
}

/**
 * Union type representing all valid JSON payloads received by the client from the server.
 */
export type ServerMessage = JoinResponseMessage | Snapshot;
