/**
 * @fileoverview Shared game constants defining arena dimensions, player physical properties,
 * and simulation tick parameters.
 *
 * CRITICAL INVARIANT:
 * These constants MUST remain in exact synchronization with the backend Go service physics
 * definitions in `service/physics/constants.go`. Any discrepancy between client and server
 * constants will lead to simulation divergence and reconciliation jitter.
 */

/**
 * The total width of the playable game arena in pixels.
 * Clamps maximum horizontal coordinate travel for players and projectiles.
 */
export const ARENA_W = 1200;

/**
 * The total height of the playable game arena in pixels.
 * Clamps maximum vertical coordinate travel for players and projectiles.
 */
export const ARENA_H = 800;

/**
 * Collision and visual rendering radius of each player entity in pixels.
 * Also defines the boundary inset used when clamping player positions to the arena borders.
 */
export const PLAYER_RADIUS = 20;

/**
 * Authoritative player movement speed in pixels per second.
 * Determines distance traversed per second when directional movement keys are pressed.
 */
export const PLAYER_SPEED = 200;

/**
 * Authoritative server tick rate in Hertz (updates per second).
 * Dictates server simulation frequency and client command dispatch pacing.
 */
export const TICK_RATE = 64;

/**
 * Duration of a single authoritative simulation tick in milliseconds (~15.625 ms).
 * Calculated directly from TICK_RATE (1000ms / 64).
 */
export const TICK_DURATION = 1000 / TICK_RATE;
