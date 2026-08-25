package physics_test

import (
	"math"
	"testing"

	"github.com/server-tick-netcode/service/physics"
	"github.com/server-tick-netcode/service/world"
)

// TestBullet_Spawn verifies bullet initial values, velocity vectors, and orientation.
func TestBullet_Spawn(t *testing.T) {
	physics.ResetBulletCounter()

	angle := math.Pi / 4.0 // 45 degrees
	shooterID := "player-1"
	startX := 100.0
	startY := 200.0
	currentTick := int64(42)

	bullet := physics.SpawnBullet(shooterID, startX, startY, angle, currentTick)

	if bullet == nil {
		t.Fatalf("expected non-nil bullet")
	}

	if bullet.OwnerID != shooterID {
		t.Errorf("expected OwnerID %s, got %s", shooterID, bullet.OwnerID)
	}

	if bullet.X != startX || bullet.Y != startY {
		t.Errorf("expected bullet position (%.1f, %.1f), got (%.1f, %.1f)", startX, startY, bullet.X, bullet.Y)
	}

	expectedVX := math.Cos(angle) * physics.BulletSpeed
	expectedVY := math.Sin(angle) * physics.BulletSpeed

	if math.Abs(bullet.VX-expectedVX) > 1e-6 || math.Abs(bullet.VY-expectedVY) > 1e-6 {
		t.Errorf("expected velocity (%.4f, %.4f), got (%.4f, %.4f)", expectedVX, expectedVY, bullet.VX, bullet.VY)
	}

	if bullet.BornTick != currentTick {
		t.Errorf("expected BornTick %d, got %d", currentTick, bullet.BornTick)
	}
}

// TestBullet_CooldownEnforcement verifies that players cannot fire faster than FireCooldownTicks.
func TestBullet_CooldownEnforcement(t *testing.T) {
	player := &world.PlayerState{
		ID:           "shooter",
		X:            500,
		Y:            400,
		Health:       100,
		LastFireTick: 0,
	}

	// First shot should always be allowed
	if !physics.CanFire(player, 10) {
		t.Errorf("expected player to be able to fire first shot at tick 10")
	}

	// Update last fire tick to tick 10
	player.LastFireTick = 10

	// Immediate next tick should be denied due to cooldown
	if physics.CanFire(player, 11) {
		t.Errorf("expected player firing to be on cooldown at tick 11")
	}

	// Tick 25 is 15 ticks later (< 16 cooldown), must be denied
	if physics.CanFire(player, 25) {
		t.Errorf("expected player firing to be on cooldown at tick 25")
	}

	// Tick 26 is exactly 16 ticks later (>= FireCooldownTicks), must be permitted
	if !physics.CanFire(player, 26) {
		t.Errorf("expected player to be able to fire at tick 26 (10 + 16)")
	}
}

// TestBullet_AdvanceAndBoundaryCull verifies that bullets advance kinematically and
// bullets traveling outside the arena are removed.
func TestBullet_AdvanceAndBoundaryCull(t *testing.T) {
	bullets := []*world.BulletState{
		{
			ID:      "b-in-bounds",
			OwnerID: "p1",
			X:       500.0,
			Y:       400.0,
			VX:      physics.BulletSpeed, // moving right
			VY:      0.0,
		},
		{
			ID:      "b-near-right-edge",
			OwnerID: "p2",
			X:       physics.ArenaW - 10.0,
			Y:       400.0,
			VX:      physics.BulletSpeed, // will cross right edge
			VY:      0.0,
		},
		{
			ID:      "b-near-top-edge",
			OwnerID: "p3",
			X:       400.0,
			Y:       5.0,
			VX:      0.0,
			VY:      -physics.BulletSpeed, // will cross top edge (< 0)
		},
	}

	dt := physics.TickDuration // 1/64 s = ~9.375 pixels at 600px/s

	active := physics.AdvanceBullets(bullets, dt)

	// b-in-bounds should remain active and have moved
	// b-near-right-edge moved from 1190 to ~1199.375 (still <= 1200)
	// b-near-top-edge moved from 5 to -4.375 (culled)
	foundInBounds := false
	foundRightEdge := false
	foundTopEdge := false

	for _, b := range active {
		if b.ID == "b-in-bounds" {
			foundInBounds = true
			expectedX := 500.0 + physics.BulletSpeed*dt
			if math.Abs(b.X-expectedX) > 1e-4 {
				t.Errorf("expected in-bounds bullet X=%.4f, got %.4f", expectedX, b.X)
			}
		}
		if b.ID == "b-near-right-edge" {
			foundRightEdge = true
		}
		if b.ID == "b-near-top-edge" {
			foundTopEdge = true
		}
	}

	if !foundInBounds {
		t.Errorf("expected b-in-bounds to remain active")
	}
	if !foundRightEdge {
		t.Errorf("expected b-near-right-edge to remain active on tick 1")
	}
	if foundTopEdge {
		t.Errorf("expected b-near-top-edge to be culled (went negative Y)")
	}

	// Advance once more: b-near-right-edge should now cross 1200 and be culled
	activeAfterSecondTick := physics.AdvanceBullets(active, dt)
	for _, b := range activeAfterSecondTick {
		if b.ID == "b-near-right-edge" {
			t.Errorf("expected b-near-right-edge to be culled after second advance, but was retained at X=%.2f", b.X)
		}
	}
}

// TestBullet_SimulateBulletsIntegration verifies that SimulateBullets advances world state bullets correctly.
func TestBullet_SimulateBulletsIntegration(t *testing.T) {
	w := world.NewWorldState()
	w.Tick = 1

	p := w.AddPlayer("p1")
	p.X = 600
	p.Y = 400

	bullet, ok := physics.TryFire(w, "p1", 0.0, 1)
	if !ok || bullet == nil {
		t.Fatalf("expected successful TryFire")
	}

	if len(w.Bullets) != 1 {
		t.Fatalf("expected 1 bullet in world state, got %d", len(w.Bullets))
	}

	physics.SimulateBullets(w, physics.TickDuration)

	if len(w.Bullets) != 1 {
		t.Fatalf("expected bullet to remain in world after 1 tick")
	}

	expectedX := 600.0 + physics.BulletSpeed*physics.TickDuration
	if math.Abs(w.Bullets[0].X-expectedX) > 1e-4 {
		t.Errorf("expected bullet X=%.4f, got %.4f", expectedX, w.Bullets[0].X)
	}
}
