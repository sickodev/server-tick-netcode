package world_test

import (
	"fmt"
	"sync"
	"testing"

	"github.com/server-tick-netcode/service/world"
)

// TestHistoryBuffer_RecordAndStateAt verifies that recorded historical player frames
// can be retrieved by their exact tick number.
func TestHistoryBuffer_RecordAndStateAt(t *testing.T) {
	buffer := world.NewHistoryBuffer()

	players := map[string]*world.PlayerState{
		"p1": {ID: "p1", X: 100.0, Y: 150.0, Angle: 1.57, Health: 100, Speed: 200.0},
		"p2": {ID: "p2", X: 400.0, Y: 300.0, Angle: 3.14, Health: 75, Speed: 200.0},
	}

	buffer.Record(10, players)

	frame := buffer.StateAt(10)
	if frame == nil {
		t.Fatalf("expected historical frame at tick 10, got nil")
	}

	if frame.Tick != 10 {
		t.Errorf("expected frame tick 10, got %d", frame.Tick)
	}

	if len(frame.Players) != 2 {
		t.Fatalf("expected 2 players, got %d", len(frame.Players))
	}

	p1, ok := frame.Players["p1"]
	if !ok || p1.X != 100.0 || p1.Y != 150.0 || p1.Health != 100 {
		t.Errorf("unexpected p1 state: %+v", p1)
	}

	p2, ok := frame.Players["p2"]
	if !ok || p2.X != 400.0 || p2.Y != 300.0 || p2.Health != 75 {
		t.Errorf("unexpected p2 state: %+v", p2)
	}
}

// TestHistoryBuffer_DeepCopyIsolation ensures that mutating live player objects after recording
// does not modify historical frames stored in the buffer.
func TestHistoryBuffer_DeepCopyIsolation(t *testing.T) {
	buffer := world.NewHistoryBuffer()

	originalPlayer := &world.PlayerState{
		ID:     "p1",
		X:      100.0,
		Y:      100.0,
		Angle:  0.0,
		Health: 100,
		Speed:  200.0,
	}
	players := map[string]*world.PlayerState{"p1": originalPlayer}

	buffer.Record(5, players)

	// Mutate original live player state
	originalPlayer.X = 999.0
	originalPlayer.Y = 888.0
	originalPlayer.Health = 25

	frame := buffer.StateAt(5)
	if frame == nil {
		t.Fatalf("expected frame at tick 5, got nil")
	}

	historicalP1 := frame.Players["p1"]
	if historicalP1.X != 100.0 || historicalP1.Y != 100.0 || historicalP1.Health != 100 {
		t.Errorf("deep copy failed: historical state modified by live state changes: %+v", historicalP1)
	}

	// Mutating the returned historical frame should not affect future retrievals
	historicalP1.X = 555.0
	frameSecondGet := buffer.StateAt(5)
	if frameSecondGet.Players["p1"].X != 100.0 {
		t.Errorf("defensive copy failed: frame returned from StateAt mutated internal buffer")
	}
}

// TestHistoryBuffer_RingBufferWrapAndExpiry verifies DoD requirement:
// Record 200 states into the 128-slot buffer:
// Ticks 1..72 must return nil (overwritten).
// Ticks 73..200 must return valid matching states.
func TestHistoryBuffer_RingBufferWrapAndExpiry(t *testing.T) {
	buffer := world.NewHistoryBuffer()

	const totalTicks = 200
	for tick := int64(1); tick <= totalTicks; tick++ {
		players := map[string]*world.PlayerState{
			"p1": {
				ID:     "p1",
				X:      float64(tick * 10),
				Y:      float64(tick * 5),
				Angle:  0.0,
				Health: 100,
				Speed:  200.0,
			},
		}
		buffer.Record(tick, players)
	}

	// Assert ticks 1..72 return nil (overwritten)
	for tick := int64(1); tick <= 72; tick++ {
		frame := buffer.StateAt(tick)
		if frame != nil {
			t.Errorf("expected tick %d to be overwritten (nil), but got valid frame: %+v", tick, frame)
		}
	}

	// Assert ticks 73..200 return valid matching data
	for tick := int64(73); tick <= totalTicks; tick++ {
		frame := buffer.StateAt(tick)
		if frame == nil {
			t.Fatalf("expected valid frame for tick %d, got nil", tick)
		}
		if frame.Tick != tick {
			t.Errorf("expected frame tick %d, got %d", tick, frame.Tick)
		}
		p1, exists := frame.Players["p1"]
		if !exists {
			t.Fatalf("tick %d: player p1 missing", tick)
		}
		expectedX := float64(tick * 10)
		if p1.X != expectedX {
			t.Errorf("tick %d: expected X=%.1f, got %.1f", tick, expectedX, p1.X)
		}
	}

	// Assert future ticks return nil
	if frame := buffer.StateAt(totalTicks + 1); frame != nil {
		t.Errorf("expected future tick %d to return nil, got %+v", totalTicks+1, frame)
	}
}

// TestHistoryBuffer_ClosestStateAt verifies clamping behavior to oldest and newest available ticks.
func TestHistoryBuffer_ClosestStateAt(t *testing.T) {
	buffer := world.NewHistoryBuffer()

	// Empty buffer should return nil
	if frame := buffer.ClosestStateAt(50); frame != nil {
		t.Errorf("expected nil for empty buffer, got %+v", frame)
	}

	// Record ticks 100 to 150
	for tick := int64(100); tick <= 150; tick++ {
		buffer.Record(tick, map[string]*world.PlayerState{
			"p1": {ID: "p1", X: float64(tick), Y: 0, Health: 100, Speed: 200},
		})
	}

	// Requesting an older tick should clamp to earliest available (100)
	earliestFrame := buffer.ClosestStateAt(50)
	if earliestFrame == nil || earliestFrame.Tick != 100 {
		t.Errorf("expected tick 100 for clamped early request, got %+v", earliestFrame)
	}

	// Requesting a future tick should clamp to latest available (150)
	latestFrame := buffer.ClosestStateAt(200)
	if latestFrame == nil || latestFrame.Tick != 150 {
		t.Errorf("expected tick 150 for clamped future request, got %+v", latestFrame)
	}

	// Requesting an exact tick within range
	exactFrame := buffer.ClosestStateAt(125)
	if exactFrame == nil || exactFrame.Tick != 125 {
		t.Errorf("expected tick 125, got %+v", exactFrame)
	}
}

// TestHistoryBuffer_Bounds verifies calculation of earliest and latest tick boundaries.
func TestHistoryBuffer_Bounds(t *testing.T) {
	buffer := world.NewHistoryBuffer()

	earliest, latest := buffer.Bounds()
	if earliest != 0 || latest != 0 {
		t.Errorf("expected (0, 0) bounds on empty buffer, got (%d, %d)", earliest, latest)
	}

	for tick := int64(1); tick <= 50; tick++ {
		buffer.Record(tick, nil)
	}
	earliest, latest = buffer.Bounds()
	if earliest != 1 || latest != 50 {
		t.Errorf("expected bounds (1, 50), got (%d, %d)", earliest, latest)
	}

	for tick := int64(51); tick <= 200; tick++ {
		buffer.Record(tick, nil)
	}
	earliest, latest = buffer.Bounds()
	if earliest != 73 || latest != 200 {
		t.Errorf("expected bounds (73, 200), got (%d, %d)", earliest, latest)
	}
}

// TestHistoryBuffer_ConcurrentReadWrite verifies thread safety under concurrent writes and reads.
func TestHistoryBuffer_ConcurrentReadWrite(t *testing.T) {
	buffer := world.NewHistoryBuffer()
	var wg sync.WaitGroup

	// Writer goroutine simulating 64Hz server ticks
	wg.Add(1)
	go func() {
		defer wg.Done()
		for tick := int64(1); tick <= 300; tick++ {
			players := map[string]*world.PlayerState{
				fmt.Sprintf("p%d", tick%5): {
					ID:     fmt.Sprintf("p%d", tick%5),
					X:      float64(tick),
					Y:      float64(tick * 2),
					Angle:  0,
					Health: 100,
					Speed:  200,
				},
			}
			buffer.Record(tick, players)
		}
	}()

	// Multiple concurrent reader goroutines simulating lag-compensation raycasts
	for r := 0; r < 8; r++ {
		wg.Add(1)
		go func(readerID int) {
			defer wg.Done()
			for i := 0; i < 200; i++ {
				tick := int64((i*7 + readerID*13) % 300)
				_ = buffer.StateAt(tick)
				_ = buffer.ClosestStateAt(tick)
				_, _ = buffer.Bounds()
			}
		}(r)
	}

	wg.Wait()
}
