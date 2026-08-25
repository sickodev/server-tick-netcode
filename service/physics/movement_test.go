package physics

import (
	"math/rand"
	"testing"

	"github.com/server-tick-netcode/service/world"
)

// TestApplyMovementDeterminism runs 1000 identical iterations on two separate player states
// and verifies that float calculations produce bit-exact identical values across runs.
func TestApplyMovementDeterminism(t *testing.T) {
	const iterations = 1000
	const seed = 42

	// Run 1
	rng1 := rand.New(rand.NewSource(seed))
	p1 := &world.PlayerState{ID: "p1", X: 500, Y: 400, Speed: PlayerSpeed}
	for i := 0; i < iterations; i++ {
		dx := (rng1.Float64() * 2) - 1
		dy := (rng1.Float64() * 2) - 1
		angle := rng1.Float64() * 6.28
		ApplyMovement(p1, dx, dy, angle, TickDuration)
	}

	// Run 2
	rng2 := rand.New(rand.NewSource(seed))
	p2 := &world.PlayerState{ID: "p2", X: 500, Y: 400, Speed: PlayerSpeed}
	for i := 0; i < iterations; i++ {
		dx := (rng2.Float64() * 2) - 1
		dy := (rng2.Float64() * 2) - 1
		angle := rng2.Float64() * 6.28
		ApplyMovement(p2, dx, dy, angle, TickDuration)
	}

	if p1.X != p2.X || p1.Y != p2.Y || p1.Angle != p2.Angle {
		t.Fatalf("Determinism failure: p1=(%f,%f,%f) != p2=(%f,%f,%f)", p1.X, p1.Y, p1.Angle, p2.X, p2.Y, p2.Angle)
	}
}

// TestApplyMovementBoundaryClamping verifies players cannot exceed arena limits.
func TestApplyMovementBoundaryClamping(t *testing.T) {
	p := &world.PlayerState{ID: "p1", X: ArenaW, Y: ArenaH, Speed: PlayerSpeed}

	// Attempt to move beyond bottom-right
	ApplyMovement(p, 1.0, 1.0, 0.0, 1.0)
	if p.X > ArenaW-PlayerRadius || p.Y > ArenaH-PlayerRadius {
		t.Errorf("Clamping failed on max bounds: got (%f, %f)", p.X, p.Y)
	}

	// Attempt to move beyond top-left
	p.X = 0
	p.Y = 0
	ApplyMovement(p, -1.0, -1.0, 0.0, 1.0)
	if p.X < PlayerRadius || p.Y < PlayerRadius {
		t.Errorf("Clamping failed on min bounds: got (%f, %f)", p.X, p.Y)
	}
}

// TestProcessCommandsLatestOnly verifies that when multiple commands are queued in one tick,
// only the latest command is applied and LastAckSeq is updated.
func TestProcessCommandsLatestOnly(t *testing.T) {
	w := world.NewWorldState()
	p := w.AddPlayer("p1")
	p.X = 500
	p.Y = 500

	// Enqueue three commands
	w.EnqueueCmd("p1", world.UserCmd{Seq: 1, DX: 1, DY: 0, AimAngle: 1.0})
	w.EnqueueCmd("p1", world.UserCmd{Seq: 2, DX: 0, DY: 1, AimAngle: 2.0})
	w.EnqueueCmd("p1", world.UserCmd{Seq: 3, DX: -1, DY: 0, AimAngle: 3.14})

	// Process one tick
	ProcessCommands(w, TickDuration)

	// Since only cmd seq 3 (DX: -1, DY: 0) is applied:
	expectedX := 500.0 - PlayerSpeed*TickDuration
	if p.X != expectedX {
		t.Errorf("Expected X %f, got %f", expectedX, p.X)
	}
	if p.Y != 500.0 {
		t.Errorf("Expected Y 500.0, got %f", p.Y)
	}
	if p.Angle != 3.14 {
		t.Errorf("Expected Angle 3.14, got %f", p.Angle)
	}
	if w.LastAckSeq["p1"] != 3 {
		t.Errorf("Expected LastAckSeq 3, got %d", w.LastAckSeq["p1"])
	}
}
