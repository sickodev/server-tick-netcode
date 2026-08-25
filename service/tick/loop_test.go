package tick

import (
	"context"
	"sync/atomic"
	"testing"
	"time"
)

// TestLoopExecution tests that Loop fires ticks continuously and shuts down on context cancellation.
func TestLoopExecution(t *testing.T) {
	var tickCount int64
	loop := NewLoop(func(tick int64) {
		atomic.StoreInt64(&tickCount, tick)
	})

	ctx, cancel := context.WithTimeout(context.Background(), 250*time.Millisecond)
	defer cancel()

	// Start loop in goroutine
	done := make(chan struct{})
	go func() {
		loop.Start(ctx)
		close(done)
	}()

	select {
	case <-done:
		// Completed cleanly on context timeout
	case <-time.After(2 * time.Second):
		t.Fatal("Loop did not exit after context timeout")
	}

	finalTicks := atomic.LoadInt64(&tickCount)
	if finalTicks == 0 {
		t.Errorf("Expected ticks to have fired, got %d", finalTicks)
	}
}
