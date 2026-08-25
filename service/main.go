// Package main is the entry point for the authoritative Go game service.
// It initializes core subsystems and orchestrates the server lifecycle.
package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"

	"github.com/server-tick-netcode/service/physics"
	"github.com/server-tick-netcode/service/tick"
	"github.com/server-tick-netcode/service/world"
)

// main initializes signal traps, creates world state, and executes the game loop.
func main() {
	fmt.Println("game service starting…")

	// Set up signal listening for graceful shutdown upon SIGINT (Ctrl+C) or SIGTERM (systemd/k8s/docker).
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// Initialize the authoritative global world state.
	worldState := world.NewWorldState()

	// Instantiate the tick loop with simulation processing and counter output.
	loop := tick.NewLoop(func(t int64) {
		worldState.Mu.Lock()
		worldState.Tick = t
		worldState.Mu.Unlock()

		// Drain client input queues and apply movement physics for all connected players.
		physics.ProcessCommands(worldState, physics.TickDuration)

		// Print periodic tick diagnostic to stdout once per second (every 64 ticks).
		if t%physics.TickRate == 0 {
			fmt.Printf("tick: %d\n", t)
		}
	})

	// Start the tick loop synchronously until context cancellation is received.
	loop.Start(ctx)

	fmt.Println("game service shutting down…")
}
