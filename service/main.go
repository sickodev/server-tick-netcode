// Package main is the entry point for the authoritative Go game service.
// It initializes core subsystems and orchestrates the server lifecycle.
package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const (
	// TickRate represents the server tick frequency in Hz (64 updates per second).
	// Higher tick rates provide smoother simulation at the expense of CPU cycles.
	TickRate = 64

	// TickInterval is the duration between simulation ticks (15.625ms for 64Hz).
	TickInterval = time.Second / TickRate
)

// main initializes signal traps and executes a 64Hz tick loop until an OS termination signal is received.
func main() {
	fmt.Println("game service starting…")

	// Set up signal listening for graceful shutdown upon SIGINT (Ctrl+C) or SIGTERM (systemd/k8s/docker).
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// Initialize the fixed-rate ticker configured for 64Hz simulation.
	ticker := time.NewTicker(TickInterval)
	defer ticker.Stop()

	// tick tracks the monotonic tick counter since the server started.
	var tick int64

	// Main tick loop runs until context cancellation is triggered by an OS interrupt.
	for {
		select {
		case <-ctx.Done():
			// Shutdown signal caught; exit the loop gracefully to prevent goroutine leaks.
			fmt.Println("game service shutting down…")
			return
		case <-ticker.C:
			tick++
			// Print the tick counter once per second (every 64 ticks).
			if tick%TickRate == 0 {
				fmt.Printf("tick: %d\n", tick)
			}
		}
	}
}
