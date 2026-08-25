package world_test

import (
	"testing"
	"time"

	"github.com/server-tick-netcode/service/world"
)

// TestApplyDamage_StandardHit verifies that damage reduces target health by the exact damage amount.
func TestApplyDamage_StandardHit(t *testing.T) {
	w := world.NewWorldState()
	target := w.AddPlayer("target")
	target.Health = 100

	remHealth, eliminated := w.ApplyDamage("shooter", "target", 25, 0)
	if remHealth != 75 {
		t.Errorf("expected 75 HP, got %d", remHealth)
	}
	if eliminated {
		t.Errorf("expected player not to be eliminated at 75 HP")
	}

	w.Mu.RLock()
	p := w.Players["target"]
	w.Mu.RUnlock()
	if p == nil || p.Health != 75 {
		t.Errorf("expected player state in world to reflect 75 HP, got %+v", p)
	}
}

// TestApplyDamage_FloorAtZero verifies that damage cannot reduce health below 0.
func TestApplyDamage_FloorAtZero(t *testing.T) {
	w := world.NewWorldState()
	target := w.AddPlayer("target")
	target.Health = 10

	remHealth, eliminated := w.ApplyDamage("shooter", "target", 50, 0)
	if remHealth != 0 {
		t.Errorf("expected health to be floored at 0, got %d", remHealth)
	}
	if !eliminated {
		t.Errorf("expected elimination flag when health reaches 0")
	}
}

// TestApplyDamage_EliminationAndRespawn verifies full 4-hit elimination sequence and respawn.
func TestApplyDamage_EliminationAndRespawn(t *testing.T) {
	w := world.NewWorldState()
	target := w.AddPlayer("target")
	target.Health = 100

	// Hit 1: 100 -> 75
	h1, elim1 := w.ApplyDamage("shooter", "target", 25, 0)
	if h1 != 75 || elim1 {
		t.Errorf("Hit 1: expected 75 HP, got %d (elim: %v)", h1, elim1)
	}

	// Hit 2: 75 -> 50
	h2, elim2 := w.ApplyDamage("shooter", "target", 25, 0)
	if h2 != 50 || elim2 {
		t.Errorf("Hit 2: expected 50 HP, got %d (elim: %v)", h2, elim2)
	}

	// Hit 3: 50 -> 25
	h3, elim3 := w.ApplyDamage("shooter", "target", 25, 0)
	if h3 != 25 || elim3 {
		t.Errorf("Hit 3: expected 25 HP, got %d (elim: %v)", h3, elim3)
	}

	// Hit 4: 25 -> 0 (Elimination with 50ms respawn timer for fast test)
	h4, elim4 := w.ApplyDamage("shooter", "target", 25, 50*time.Millisecond)
	if h4 != 0 || !elim4 {
		t.Errorf("Hit 4: expected 0 HP with elimination, got %d (elim: %v)", h4, elim4)
	}

	// Target should be removed from active players map immediately
	w.Mu.RLock()
	eliminatedPlayer := w.Players["target"]
	w.Mu.RUnlock()
	if eliminatedPlayer != nil {
		t.Errorf("expected eliminated player to be removed immediately from Players map, got %+v", eliminatedPlayer)
	}

	// Wait for respawn timer to trigger
	time.Sleep(100 * time.Millisecond)

	w.Mu.RLock()
	respawnedPlayer := w.Players["target"]
	w.Mu.RUnlock()

	if respawnedPlayer == nil {
		t.Fatalf("expected player to respawn after timer, but player was nil")
	}

	if respawnedPlayer.Health != world.InitialHealth {
		t.Errorf("expected respawned player to have full health %d, got %d", world.InitialHealth, respawnedPlayer.Health)
	}

	// Verify respawn coordinates are within inner 80% bounds
	minX := world.ArenaW * 0.10 // 120
	maxX := world.ArenaW * 0.90 // 1080
	minY := world.ArenaH * 0.10 // 80
	maxY := world.ArenaH * 0.90 // 720

	if respawnedPlayer.X < minX || respawnedPlayer.X > maxX || respawnedPlayer.Y < minY || respawnedPlayer.Y > maxY {
		t.Errorf("respawn coordinates (%.2f, %.2f) out of inner 80%% bounds [%.1f-%.1f, %.1f-%.1f]",
			respawnedPlayer.X, respawnedPlayer.Y, minX, maxX, minY, maxY)
	}
}
