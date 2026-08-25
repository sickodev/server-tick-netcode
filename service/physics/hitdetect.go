// Package physics provides collision geometry, lag-compensated hit detection, and spatial queries.
package physics

import (
	"math"

	"github.com/server-tick-netcode/service/world"
)

// HitResult encapsulates the outcome of a verified projectile or raycast hit on a target entity.
type HitResult struct {
	// ShooterID is the player ID who fired the shot.
	ShooterID string
	// TargetID is the player ID of the entity that was struck.
	TargetID string
	// BulletID is the unique identifier of the bullet that caused the impact (empty for instant raycasts).
	BulletID string
	// Damage is the hit point deduction to be applied.
	Damage int
	// HitTick is the historical server tick at which the collision was validated.
	HitTick int64
	// HitX is the horizontal impact coordinate in arena space.
	HitX float64
	// HitY is the vertical impact coordinate in arena space.
	HitY float64
}

// RayVsCircle performs a 2D ray-to-circle intersection test.
// originX, originY: starting point of the ray.
// dirX, dirY: ray direction vector (does not need to be normalized, but non-zero).
// centerX, centerY: center coordinates of the target circle.
// radius: collision radius of the target circle.
// Returns true if the ray originates inside the circle or intersects its boundary in the positive direction.
func RayVsCircle(originX, originY, dirX, dirY, centerX, centerY, radius float64) bool {
	hit, _ := RayVsCircleDistance(originX, originY, dirX, dirY, centerX, centerY, radius)
	return hit
}

// RayVsCircleDistance tests 2D ray-to-circle intersection and returns the distance along the ray to the entry point.
// Returns (true, distance) if an intersection occurs in the forward ray direction; otherwise (false, 0).
func RayVsCircleDistance(originX, originY, dirX, dirY, centerX, centerY, radius float64) (bool, float64) {
	// Vector from ray origin to circle center
	vcx := centerX - originX
	vcy := centerY - originY

	// Normalize direction vector
	dirLen := math.Hypot(dirX, dirY)
	if dirLen < 1e-9 {
		// Degenerate direction vector: check if origin is inside circle
		if vcx*vcx+vcy*vcy <= radius*radius {
			return true, 0.0
		}
		return false, 0.0
	}
	ndx := dirX / dirLen
	ndy := dirY / dirLen

	// Project circle center vector onto ray direction vector
	t := vcx*ndx + vcy*ndy

	// Distance squared from circle center to origin
	centerDistSq := vcx*vcx + vcy*vcy
	radiusSq := radius * radius

	// If origin is inside the circle, it is an immediate hit at distance 0
	if centerDistSq <= radiusSq {
		return true, 0.0
	}

	// If circle center is behind the ray origin and origin is outside the circle, no intersection
	if t < 0 {
		return false, 0.0
	}

	// Perpendicular distance squared from circle center to the ray
	dSq := centerDistSq - t*t
	if dSq > radiusSq {
		return false, 0.0
	}

	// Distance from projection point to sphere boundary along ray
	halfChord := math.Sqrt(math.Max(0.0, radiusSq-dSq))
	hitDistance := t - halfChord

	return true, hitDistance
}

// SegmentVsCircle tests intersection between a finite line segment (e.g. bullet step in one tick) and a circle.
// startX, startY: starting point of the segment.
// endX, endY: ending point of the segment.
// centerX, centerY: center coordinates of the target circle.
// radius: collision radius of the target circle.
func SegmentVsCircle(startX, startY, endX, endY, centerX, centerY, radius float64) bool {
	segX := endX - startX
	segY := endY - startY
	segLen := math.Hypot(segX, segY)

	// Degenerate segment: test point-in-circle
	if segLen < 1e-9 {
		distSq := (centerX-startX)*(centerX-startX) + (centerY-startY)*(centerY-startY)
		return distSq <= radius*radius
	}

	ndx := segX / segLen
	ndy := segY / segLen

	vcx := centerX - startX
	vcy := centerY - startY

	// Projection of circle center onto segment line
	t := vcx*ndx + vcy*ndy

	// Clamp projection to segment endpoints [0, segLen]
	clampedT := math.Max(0.0, math.Min(segLen, t))

	// Closest point on the finite segment
	closestX := startX + clampedT*ndx
	closestY := startY + clampedT*ndy

	dx := centerX - closestX
	dy := centerY - closestY
	distSq := dx*dx + dy*dy

	return distSq <= radius*radius
}

// CalculateRewindTick determines the historical server tick corresponding to the client's rendered view.
// Formula: rewindTick = currentTick - clientLatencyTicks - InterpTicks
// clientLatencyTicks is the estimated one-way network transit time in tick units.
// InterpTicks accounts for the client-side entity interpolation buffer delay (~2 ticks).
func CalculateRewindTick(currentTick int64, clientLatencyTicks int64) int64 {
	if clientLatencyTicks < 0 {
		clientLatencyTicks = 0
	}
	rewindTick := currentTick - clientLatencyTicks - InterpTicks
	if rewindTick < 1 {
		rewindTick = 1
	}
	return rewindTick
}

// CheckBulletHits performs lag-compensated collision testing for an active projectile against historical player hitboxes.
// history is the ring buffer containing past world snapshots.
// bullet is the active projectile being evaluated.
// shooterID is the player ID who discharged the projectile.
// rewindTick is the authoritative historical tick at which targets are evaluated.
// Returns (*HitResult, true) if an impact occurred, or (nil, false) otherwise.
func CheckBulletHits(history *world.HistoryBuffer, bullet *world.BulletState, shooterID string, rewindTick int64) (*HitResult, bool) {
	if history == nil || bullet == nil {
		return nil, false
	}

	frame := history.ClosestStateAt(rewindTick)
	if frame == nil || len(frame.Players) == 0 {
		return nil, false
	}

	// Bullet previous position in the prior tick
	prevX := bullet.X - bullet.VX*TickDuration
	prevY := bullet.Y - bullet.VY*TickDuration

	effectiveRadius := PlayerRadius + BulletRadius

	var bestResult *HitResult
	var minDistance float64 = math.MaxFloat64

	for id, target := range frame.Players {
		// Disallow self-damage and hitting eliminated players
		if id == shooterID || target.Health <= 0 {
			continue
		}

		// Check segment collision for the bullet's path during this tick
		if SegmentVsCircle(prevX, prevY, bullet.X, bullet.Y, target.X, target.Y, effectiveRadius) {
			dx := target.X - prevX
			dy := target.Y - prevY
			dist := dx*dx + dy*dy

			if dist < minDistance {
				minDistance = dist
				bestResult = &HitResult{
					ShooterID: shooterID,
					TargetID:  id,
					BulletID:  bullet.ID,
					Damage:    BulletDamage,
					HitTick:   frame.Tick,
					HitX:      target.X,
					HitY:      target.Y,
				}
			}
		}
	}

	if bestResult != nil {
		return bestResult, true
	}
	return nil, false
}

// CheckRayHit casts an instantaneous ray from the shooter's coordinates along aimAngle at the specified rewindTick.
// It checks all other players at that historical moment and returns the closest valid target hit.
func CheckRayHit(history *world.HistoryBuffer, shooterID string, originX, originY, aimAngle float64, rewindTick int64) (*HitResult, bool) {
	if history == nil {
		return nil, false
	}

	frame := history.ClosestStateAt(rewindTick)
	if frame == nil || len(frame.Players) == 0 {
		return nil, false
	}

	dirX := math.Cos(aimAngle)
	dirY := math.Sin(aimAngle)

	var bestResult *HitResult
	var minHitDist float64 = math.MaxFloat64

	for id, target := range frame.Players {
		if id == shooterID || target.Health <= 0 {
			continue
		}

		hit, hitDist := RayVsCircleDistance(originX, originY, dirX, dirY, target.X, target.Y, PlayerRadius)
		if hit && hitDist < minHitDist {
			minHitDist = hitDist
			bestResult = &HitResult{
				ShooterID: shooterID,
				TargetID:  id,
				BulletID:  "",
				Damage:    BulletDamage,
				HitTick:   frame.Tick,
				HitX:      target.X,
				HitY:      target.Y,
			}
		}
	}

	if bestResult != nil {
		return bestResult, true
	}
	return nil, false
}
