// Package physics defines simulation physics constants, boundary constraints,
// and deterministic kinematic calculations for entities in the game world.
package physics

const (
	// ArenaW is the total width of the game arena in pixels.
	ArenaW = 1200.0

	// ArenaH is the total height of the game arena in pixels.
	ArenaH = 800.0

	// PlayerRadius is the collision radius of a player character in pixels.
	PlayerRadius = 20.0

	// PlayerSpeed is the movement speed of players in pixels per second.
	PlayerSpeed = 200.0

	// BulletSpeed is the travel speed of projectile bullets in pixels per second.
	BulletSpeed = 600.0

	// BulletDamage is the amount of hit points deducted when a bullet impacts a player.
	BulletDamage = 25

	// TickRate is the simulation rate in Hz (updates per second).
	TickRate = 64

	// TickDuration is the delta time in seconds for a single simulation tick (1/64 s = 0.015625 s).
	TickDuration = 1.0 / TickRate

	// HistorySize is the maximum number of historical world states retained for lag compensation.
	HistorySize = 128
)
