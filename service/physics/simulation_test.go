package physics_test

import (
	"testing"
	"time"

	"github.com/server-tick-netcode/service/physics"
	"github.com/server-tick-netcode/service/world"
)

// TestSimulateWorld_EndToEnd verifies the complete tick simulation pipeline:
// input drain -> bullet spawn -> kinematic advance -> lag-comp hit detection -> damage application -> bullet culling.
func TestSimulateWorld_EndToEnd(t *testing.T) {
	w := world.NewWorldState()
	w.Tick = 1

	shooter := w.AddPlayer("shooter")
	shooter.X = 100.0
	shooter.Y = 300.0
	shooter.Health = 100

	target := w.AddPlayer("target")
	target.X = 150.0 // target positioned 50px directly to the right
	target.Y = 300.0
	target.Health = 100

	// Tick 1: Seed history with initial player positions
	physics.SimulateWorld(w, physics.TickDuration)

	// Tick 2: Shooter fires rightwards (AimAngle = 0.0)
	w.Tick = 2
	w.EnqueueCmd("shooter", world.UserCmd{
		Seq:      1,
		DX:       0,
		DY:       0,
		AimAngle: 0.0,
		Fire:     true,
	})

	// Run simulation tick 2: bullet should spawn
	physics.SimulateWorld(w, physics.TickDuration)

	w.Mu.RLock()
	bulletCount := len(w.Bullets)
	w.Mu.RUnlock()

	if bulletCount != 1 {
		t.Fatalf("expected 1 active bullet spawned, got %d", bulletCount)
	}

	// Ticks 3..10: Advance simulation until bullet reaches target at (150, 300)
	// Speed = 600 px/s, dt = 1/64 s (~9.375 px/tick) -> ~5 ticks to cross 50px
	var hitDetected bool
	for tick := int64(3); tick <= 15; tick++ {
		w.Tick = tick
		hits := physics.SimulateWorld(w, physics.TickDuration)
		if len(hits) > 0 {
			hitDetected = true
			if hits[0].TargetID != "target" {
				t.Errorf("expected target hit, got %s", hits[0].TargetID)
			}
			if hits[0].Damage != physics.BulletDamage {
				t.Errorf("expected %d damage, got %d", physics.BulletDamage, hits[0].Damage)
			}
			break
		}
	}

	if !hitDetected {
		t.Fatalf("expected bullet to hit target within 15 ticks")
	}

	// Verify target health reduced to 75
	w.Mu.RLock()
	targetState := w.Players["target"]
	bulletCountAfterHit := len(w.Bullets)
	w.Mu.RUnlock()

	if targetState == nil || targetState.Health != 75 {
		t.Errorf("expected target health 75, got %+v", targetState)
	}

	// Verify bullet was consumed on impact
	if bulletCountAfterHit != 0 {
		t.Errorf("expected 0 bullets after hit (bullet consumed), got %d", bulletCountAfterHit)
	}
}

// TestSimulateWorld_EliminationAndHistory verifies that eliminated players are recorded and respawned.
func TestSimulateWorld_EliminationAndHistory(t *testing.T) {
	w := world.NewWorldState()
	w.Tick = 10

	target := w.AddPlayer("target")
	target.Health = 25 // 1 shot from elimination

	// Seed history
	physics.SimulateWorld(w, physics.TickDuration)

	// Apply fatal damage directly with short respawn delay
	w.ApplyDamage("shooter", "target", 25, 20*time.Millisecond)

	w.Mu.RLock()
	_, existsBeforeRespawn := w.Players["target"]
	w.Mu.RUnlock()

	if existsBeforeRespawn {
		t.Errorf("expected target to be removed immediately from active players")
	}

	// Wait for respawn
	time.Sleep(50 * time.Millisecond)

	w.Mu.RLock()
	respawned, existsAfterRespawn := w.Players["target"]
	w.Mu.RUnlock()

	if !existsAfterRespawn || respawned == nil {
		t.Fatalf("expected target to be respawned")
	}
	if respawned.Health != 100 {
		t.Errorf("expected respawned health 100, got %d", respawned.Health)
	}
}
