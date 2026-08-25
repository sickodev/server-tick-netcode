// Package physics provides the deterministic simulation kinematics and collision boundaries.
package physics

import (
	"math"

	"github.com/server-tick-netcode/service/world"
)

// ApplyMovement updates the player's position and aim angle for one tick.
// dx and dy are normalized movement directions (-1, 0, or 1).
// aimAngle is the direction the player is facing in radians.
// dt is the delta time in seconds for the simulation tick.
// The player's position is clamped to the arena boundaries inset by PlayerRadius.
//
// CRITICAL INVARIANT:
// This function is the single source of truth for entity kinematic movement.
// Frontend PredictionEngine.applyMovement MUST match this logic and math exactly.
func ApplyMovement(p *world.PlayerState, dx, dy, aimAngle, dt float64) {
	speed := PlayerSpeed
	if p.Speed > 0 {
		speed = p.Speed
	}

	// Update positions with frame-rate/tick-rate independent delta time
	p.X += dx * speed * dt
	p.Y += dy * speed * dt
	p.Angle = aimAngle

	// Clamp to arena boundaries ensuring the player hitbox never clips outside
	p.X = math.Max(PlayerRadius, math.Min(ArenaW-PlayerRadius, p.X))
	p.Y = math.Max(PlayerRadius, math.Min(ArenaH-PlayerRadius, p.Y))
}

// ProcessCommands drains pending client commands for all players in the world and applies the latest movement.
// It acquires the world state write lock to guarantee consistency across concurrent network ingress.
func ProcessCommands(w *world.WorldState, dt float64) {
	w.Mu.Lock()
	defer w.Mu.Unlock()

	for id, q := range w.Queues {
		var latestCmd *world.UserCmd

		// Drain all pending commands in the player's queue non-blocking to isolate the latest input
	drainLoop:
		for {
			select {
			case cmd := <-q.Cmds:
				cmdCopy := cmd
				latestCmd = &cmdCopy
			default:
				break drainLoop
			}
		}

		// Apply the latest command for this tick if one was received
		if latestCmd != nil {
			if player, exists := w.Players[id]; exists {
				ApplyMovement(player, latestCmd.DX, latestCmd.DY, latestCmd.AimAngle, dt)
				w.LastAckSeq[id] = latestCmd.Seq
			}
		}
	}
}
