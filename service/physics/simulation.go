// Package physics provides simulation kinematics, collision detection, and bullet mechanics.
package physics

import (
	"time"

	"github.com/server-tick-netcode/service/world"
)

// SimulateWorld advances one authoritative server simulation tick:
// 1. Drains and processes player movement & weapon firing inputs (ProcessCommands).
// 2. Advances bullet kinematics and performs lag-compensated collision checks against past player states.
// 3. Applies damage, elimination, and respawn timer scheduling for hit players.
// 4. Culls bullets that impacted players or left the arena bounds.
// 5. Records a deep-copied snapshot of current player positions into the lag compensation HistoryBuffer.
func SimulateWorld(w *world.WorldState, dt float64) []*HitResult {
	// 1. Process incoming player movement and weapon discharge inputs
	ProcessCommands(w, dt)

	// 2. Advance active projectiles and evaluate hit detection under world state lock
	w.Mu.Lock()
	defer w.Mu.Unlock()

	activeBullets := make([]*world.BulletState, 0, len(w.Bullets))
	var hits []*HitResult

	for _, b := range w.Bullets {
		if b == nil {
			continue
		}

		// Update kinematic position
		b.X += b.VX * dt
		b.Y += b.VY * dt

		// Discard bullets traveling beyond arena boundaries
		if b.X < 0 || b.X > ArenaW || b.Y < 0 || b.Y > ArenaH {
			continue
		}

		// Calculate lag compensation rewind tick (accounting for client interpolation buffer delay)
		rewindTick := CalculateRewindTick(w.Tick, 0)

		// Test for collision against historical player hitboxes
		hit, isHit := CheckBulletHits(w.History, b, b.OwnerID, rewindTick)
		if isHit && hit != nil {
			hits = append(hits, hit)
			// Apply damage directly to target player in live state
			w.ApplyDamageUnlocked(hit.ShooterID, hit.TargetID, hit.Damage, time.Duration(RespawnDelaySeconds)*time.Second)
			// Bullet consumed on impact
			continue
		}

		activeBullets = append(activeBullets, b)
	}

	w.Bullets = activeBullets

	// 3. Record updated positions into the lag compensation history ring buffer
	w.History.Record(w.Tick, w.Players)

	return hits
}
