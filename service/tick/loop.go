// Package tick provides the fixed-rate simulation loop for the game server.
package tick

import (
	"context"
	"time"

	"github.com/server-tick-netcode/service/physics"
)

// Loop drives the fixed-rate authoritative simulation ticks.
type Loop struct {
	// OnTick is the callback invoked synchronously on every simulation tick.
	OnTick func(tick int64)
}

// NewLoop creates a new Loop instance configured with the specified OnTick callback.
func NewLoop(onTick func(tick int64)) *Loop {
	return &Loop{
		OnTick: onTick,
	}
}

// Start runs the simulation tick loop at physics.TickRate (64Hz) until ctx is cancelled.
// It stops the underlying ticker on exit to ensure zero resource/goroutine leaks.
func (l *Loop) Start(ctx context.Context) {
	ticker := time.NewTicker(time.Second / physics.TickRate)
	defer ticker.Stop()

	var tick int64
	for {
		select {
		case <-ticker.C:
			tick++
			if l.OnTick != nil {
				l.OnTick(tick)
			}
		case <-ctx.Done():
			return
		}
	}
}
