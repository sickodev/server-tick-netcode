// Package physics provides simulation kinematics, collision detection, and bullet mechanics.
package physics

import (
	"fmt"
	"math"
	"sync/atomic"

	"github.com/server-tick-netcode/service/world"
)

// bulletCounter generates monotonically increasing IDs for uniquely identifying projectile bullets.
var bulletCounter atomic.Uint64

// ResetBulletCounter resets the atomic bullet ID counter back to zero (primarily used in testing).
func ResetBulletCounter() {
	bulletCounter.Store(0)
}

// CanFire evaluates whether a player has satisfied the server-enforced weapon cooldown constraint.
// Returns true if no previous shot was fired or at least FireCooldownTicks elapsed since the last shot.
func CanFire(player *world.PlayerState, currentTick int64) bool {
	if player == nil {
		return false
	}
	if player.LastFireTick <= 0 {
		return true
	}
	return (currentTick - player.LastFireTick) >= FireCooldownTicks
}

// SpawnBullet constructs an authoritative projectile bullet traveling at BulletSpeed in aimAngle direction.
// shooterID is the player ID who discharged the weapon.
// playerX and playerY are the world coordinates where the bullet originates (shooter center).
// aimAngle is the orientation in radians.
// currentTick is the server tick at which the shot is fired.
func SpawnBullet(shooterID string, playerX, playerY, aimAngle float64, currentTick int64) *world.BulletState {
	bID := bulletCounter.Add(1)
	uniqueID := fmt.Sprintf("bullet-%s-%d-%d", shooterID, currentTick, bID)

	vx := math.Cos(aimAngle) * BulletSpeed
	vy := math.Sin(aimAngle) * BulletSpeed

	return &world.BulletState{
		ID:       uniqueID,
		OwnerID:  shooterID,
		X:        playerX,
		Y:        playerY,
		VX:       vx,
		VY:       vy,
		BornTick: currentTick,
	}
}

// AdvanceBullets simulates projectile motion for all active bullets across a time delta dt in seconds.
// Bullets that exit the arena rectangular bounds [0, ArenaW] x [0, ArenaH] are discarded.
// Returns a slice of active bullets remaining inside the arena bounds.
func AdvanceBullets(bullets []*world.BulletState, dt float64) []*world.BulletState {
	active := make([]*world.BulletState, 0, len(bullets))

	for _, b := range bullets {
		if b == nil {
			continue
		}

		// Update position by velocity vector multiplied by frame delta time
		b.X += b.VX * dt
		b.Y += b.VY * dt

		// Check if bullet left the playable arena bounds
		if b.X < 0 || b.X > ArenaW || b.Y < 0 || b.Y > ArenaH {
			continue
		}

		active = append(active, b)
	}

	return active
}

// TryFire attempts to discharge a weapon for the specified player if the firing cooldown is satisfied.
// If allowed, spawns a bullet, updates the player's LastFireTick, and appends the bullet to world state.
// Assumes the caller holds the appropriate WorldState mutex lock.
func TryFire(w *world.WorldState, shooterID string, aimAngle float64, currentTick int64) (*world.BulletState, bool) {
	player, exists := w.Players[shooterID]
	if !exists || player.Health <= 0 {
		return nil, false
	}

	if !CanFire(player, currentTick) {
		return nil, false
	}

	bullet := SpawnBullet(shooterID, player.X, player.Y, aimAngle, currentTick)
	player.LastFireTick = currentTick
	w.Bullets = append(w.Bullets, bullet)

	return bullet, true
}

// SimulateBullets updates kinematic positions of all active bullets in the world state
// and removes any that travel beyond the arena boundaries.
// Thread-safe and acquires a write lock on the world state.
func SimulateBullets(w *world.WorldState, dt float64) {
	w.Mu.Lock()
	defer w.Mu.Unlock()

	w.Bullets = AdvanceBullets(w.Bullets, dt)
}
