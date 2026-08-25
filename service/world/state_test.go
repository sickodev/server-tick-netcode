package world

import (
	"sync"
	"testing"

	"github.com/server-tick-netcode/service/proto/pb"
)

// TestAddPlayer verifies player creation, health initialization, and inner 80% bounds check.
func TestAddPlayer(t *testing.T) {
	w := NewWorldState()
	p := w.AddPlayer("p1")

	if p.ID != "p1" {
		t.Fatalf("Expected ID 'p1', got '%s'", p.ID)
	}
	if p.Health != InitialHealth {
		t.Fatalf("Expected Health %d, got %d", InitialHealth, p.Health)
	}

	minX := ArenaW * 0.10
	maxX := ArenaW * 0.90
	minY := ArenaH * 0.10
	maxY := ArenaH * 0.90

	if p.X < minX || p.X > maxX || p.Y < minY || p.Y > maxY {
		t.Fatalf("Spawn position (%f, %f) outside inner 80%% bounds [%f-%f, %f-%f]", p.X, p.Y, minX, maxX, minY, maxY)
	}
}

// TestRemovePlayer verifies that player removal clears player, queue, ack sequence, and owned bullets.
func TestRemovePlayer(t *testing.T) {
	w := NewWorldState()
	w.AddPlayer("p1")
	w.AddPlayer("p2")

	// Add bullets
	w.Mu.Lock()
	w.Bullets = append(w.Bullets,
		&BulletState{ID: "b1", OwnerID: "p1", X: 100, Y: 100},
		&BulletState{ID: "b2", OwnerID: "p2", X: 200, Y: 200},
		&BulletState{ID: "b3", OwnerID: "p1", X: 300, Y: 300},
	)
	w.Mu.Unlock()

	// Remove p1
	w.RemovePlayer("p1")

	w.Mu.RLock()
	defer w.Mu.RUnlock()

	if _, exists := w.Players["p1"]; exists {
		t.Error("Player 'p1' still exists in Players map")
	}
	if _, exists := w.Queues["p1"]; exists {
		t.Error("Player 'p1' queue still exists in Queues map")
	}
	if len(w.Bullets) != 1 || w.Bullets[0].OwnerID != "p2" {
		t.Errorf("Expected 1 remaining bullet for 'p2', got %d bullets", len(w.Bullets))
	}
}

// TestConcurrentAccess verifies thread-safety under concurrent add, remove, and enqueue operations.
func TestConcurrentAccess(t *testing.T) {
	w := NewWorldState()
	var wg sync.WaitGroup

	for i := 0; i < 20; i++ {
		wg.Add(1)
		go func(id int) {
			defer wg.Done()
			playerID := "p_concurrent"
			w.AddPlayer(playerID)
			w.EnqueueCmd(playerID, UserCmd{Seq: uint32(id), DX: 1, DY: 0, AimAngle: 0, Fire: false})
			w.RemovePlayer(playerID)
		}(i)
	}

	wg.Wait()
}

// TestSnapshotChannels verifies registration, retrieval, and unregistration of snapshot channels.
func TestSnapshotChannels(t *testing.T) {
	w := NewWorldState()
	w.AddPlayer("p1")

	ch := make(chan *pb.ServerMessage, 4)
	w.RegisterSnapshotCh("p1", ch)

	w.Mu.RLock()
	retrieved, exists := w.SnapChs["p1"]
	w.Mu.RUnlock()

	if !exists || retrieved != ch {
		t.Fatal("Snapshot channel was not properly registered")
	}

	unregCh := w.UnregisterSnapshotCh("p1")
	if unregCh != ch {
		t.Fatal("UnregisterSnapshotCh did not return the expected channel")
	}

	w.Mu.RLock()
	_, existsAfter := w.SnapChs["p1"]
	w.Mu.RUnlock()

	if existsAfter {
		t.Fatal("Snapshot channel still exists after unregistering")
	}
}

