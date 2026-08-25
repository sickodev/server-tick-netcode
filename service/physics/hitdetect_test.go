package physics_test

import (
	"math"
	"testing"

	"github.com/server-tick-netcode/service/physics"
	"github.com/server-tick-netcode/service/world"
)

// TestRayVsCircle_DirectHit verifies ray intersection when aiming straight through a circle center.
func TestRayVsCircle_DirectHit(t *testing.T) {
	// Ray from (0, 0) pointing along +X axis
	originX, originY := 0.0, 0.0
	dirX, dirY := 1.0, 0.0

	// Circle at (100, 0) with radius 20
	centerX, centerY := 100.0, 0.0
	radius := 20.0

	hit := physics.RayVsCircle(originX, originY, dirX, dirY, centerX, centerY, radius)
	if !hit {
		t.Errorf("expected direct ray hit on circle")
	}

	hitWithDist, dist := physics.RayVsCircleDistance(originX, originY, dirX, dirY, centerX, centerY, radius)
	if !hitWithDist {
		t.Errorf("expected RayVsCircleDistance to return true")
	}
	expectedDist := 80.0 // 100 - 20 (entry point)
	if math.Abs(dist-expectedDist) > 1e-6 {
		t.Errorf("expected entry distance %.2f, got %.2f", expectedDist, dist)
	}
}

// TestRayVsCircle_NearMiss verifies that a ray just outside the circle radius returns false.
func TestRayVsCircle_NearMiss(t *testing.T) {
	originX, originY := 0.0, 0.0
	dirX, dirY := 1.0, 0.0

	// Circle center offset vertically by 25px (radius is 20px -> 5px miss)
	centerX, centerY := 100.0, 25.0
	radius := 20.0

	hit := physics.RayVsCircle(originX, originY, dirX, dirY, centerX, centerY, radius)
	if hit {
		t.Errorf("expected near miss to return false, got true")
	}
}

// TestRayVsCircle_BehindOrigin verifies that a circle positioned behind the ray origin is not hit.
func TestRayVsCircle_BehindOrigin(t *testing.T) {
	originX, originY := 100.0, 0.0
	dirX, dirY := 1.0, 0.0 // pointing +X

	// Circle at (50, 0) with radius 20 (behind origin at 100)
	centerX, centerY := 50.0, 0.0
	radius := 20.0

	hit := physics.RayVsCircle(originX, originY, dirX, dirY, centerX, centerY, radius)
	if hit {
		t.Errorf("expected circle behind ray origin to return false, got true")
	}
}

// TestRayVsCircle_InsideOrigin verifies that a ray starting inside the circle returns a hit.
func TestRayVsCircle_InsideOrigin(t *testing.T) {
	originX, originY := 100.0, 0.0
	dirX, dirY := 1.0, 0.0

	centerX, centerY := 100.0, 5.0
	radius := 20.0

	hit, dist := physics.RayVsCircleDistance(originX, originY, dirX, dirY, centerX, centerY, radius)
	if !hit {
		t.Errorf("expected ray starting inside circle to hit")
	}
	if dist != 0.0 {
		t.Errorf("expected distance 0 for inside origin, got %.2f", dist)
	}
}

// TestSegmentVsCircle verifies finite segment collision tests.
func TestSegmentVsCircle(t *testing.T) {
	// Segment passing directly through circle
	if !physics.SegmentVsCircle(0, 0, 200, 0, 100, 0, 20) {
		t.Errorf("expected segment passing through circle to hit")
	}

	// Segment stopping before reaching the circle
	if physics.SegmentVsCircle(0, 0, 70, 0, 100, 0, 20) {
		t.Errorf("expected segment stopping before circle to miss")
	}

	// Segment starting after the circle
	if physics.SegmentVsCircle(130, 0, 200, 0, 100, 0, 20) {
		t.Errorf("expected segment starting after circle to miss")
	}

	// Segment grazing circle boundary (radius = 20, center Y = 20, segment along Y = 0)
	if !physics.SegmentVsCircle(0, 0, 200, 0, 100, 20, 20) {
		t.Errorf("expected tangent segment to hit circle boundary")
	}
}

// TestCalculateRewindTick verifies latency and interpolation delay compensation.
func TestCalculateRewindTick(t *testing.T) {
	currentTick := int64(100)
	clientLatencyTicks := int64(5)

	// Rewind = 100 - 5 - 2 = 93
	rewindTick := physics.CalculateRewindTick(currentTick, clientLatencyTicks)
	if rewindTick != 93 {
		t.Errorf("expected rewindTick 93, got %d", rewindTick)
	}

	// Negative latency should clamp to 0
	clampedTick := physics.CalculateRewindTick(10, -5)
	if clampedTick != 8 { // 10 - 0 - 2 = 8
		t.Errorf("expected clamped rewindTick 8, got %d", clampedTick)
	}

	// Low tick clamping at 1
	lowTick := physics.CalculateRewindTick(2, 5)
	if lowTick != 1 {
		t.Errorf("expected floor tick 1, got %d", lowTick)
	}
}

// TestCheckBulletHits_LagCompensation verifies that hit detection tests against historical positions.
func TestCheckBulletHits_LagCompensation(t *testing.T) {
	history := world.NewHistoryBuffer()

	// At tick 50, target player "target1" was at (300, 300)
	history.Record(50, map[string]*world.PlayerState{
		"shooter": {ID: "shooter", X: 100, Y: 300, Health: 100, Speed: 200},
		"target1": {ID: "target1", X: 300, Y: 300, Health: 100, Speed: 200},
	})

	// By tick 55, target player "target1" has moved to (500, 300)
	history.Record(55, map[string]*world.PlayerState{
		"shooter": {ID: "shooter", X: 100, Y: 300, Health: 100, Speed: 200},
		"target1": {ID: "target1", X: 500, Y: 300, Health: 100, Speed: 200},
	})

	// Shooter fires at (300, 300) where target was at tick 50
	// Bullet in tick 55 is moving through (300, 300)
	bullet := &world.BulletState{
		ID:      "b1",
		OwnerID: "shooter",
		X:       305.0, // current position
		Y:       300.0,
		VX:      physics.BulletSpeed,
		VY:      0.0,
		BornTick: 50,
	}

	// 1. Evaluate with rewind to tick 50 (when target was at 300, 300) -> HIT!
	hit, ok := physics.CheckBulletHits(history, bullet, "shooter", 50)
	if !ok || hit == nil {
		t.Fatalf("expected hit against historical position at tick 50")
	}
	if hit.TargetID != "target1" {
		t.Errorf("expected target1, got %s", hit.TargetID)
	}
	if hit.Damage != physics.BulletDamage {
		t.Errorf("expected damage %d, got %d", physics.BulletDamage, hit.Damage)
	}

	// 2. Evaluate with rewind to tick 55 (when target has moved to 500, 300) -> MISS at (300, 300)
	hitCurrent, okCurrent := physics.CheckBulletHits(history, bullet, "shooter", 55)
	if okCurrent || hitCurrent != nil {
		t.Errorf("expected miss at tick 55 because target moved away")
	}

	// 3. Shooter cannot hit themselves
	selfHit, selfOk := physics.CheckBulletHits(history, bullet, "target1", 50)
	if selfOk || selfHit != nil {
		t.Errorf("expected self-hit to be ignored")
	}
}

// TestCheckRayHit_InstantHit verifies instantaneous lag-compensated raycast hit detection.
func TestCheckRayHit_InstantHit(t *testing.T) {
	history := world.NewHistoryBuffer()

	history.Record(20, map[string]*world.PlayerState{
		"shooter": {ID: "shooter", X: 100, Y: 200, Health: 100, Speed: 200},
		"p_hit":   {ID: "p_hit", X: 300, Y: 200, Health: 100, Speed: 200},
		"p_miss":  {ID: "p_miss", X: 300, Y: 500, Health: 100, Speed: 200},
	})

	// Ray from shooter (100, 200) pointing right (angle 0)
	hit, ok := physics.CheckRayHit(history, "shooter", 100, 200, 0.0, 20)
	if !ok || hit == nil {
		t.Fatalf("expected hit on p_hit")
	}
	if hit.TargetID != "p_hit" {
		t.Errorf("expected target p_hit, got %s", hit.TargetID)
	}

	// Ray pointing down (angle pi/2) towards (100, 500) -> misses both
	missHit, missOk := physics.CheckRayHit(history, "shooter", 100, 200, math.Pi/2.0, 20)
	if missOk || missHit != nil {
		t.Errorf("expected miss for ray pointing away from targets")
	}
}
