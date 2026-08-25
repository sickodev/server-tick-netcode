// Package main is the entry point for the authoritative Go game service.
// It initializes core subsystems and orchestrates the server lifecycle.
package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	netcodeNet "github.com/server-tick-netcode/service/net"
	"github.com/server-tick-netcode/service/physics"
	"github.com/server-tick-netcode/service/tick"
	"github.com/server-tick-netcode/service/world"
	"google.golang.org/grpc"
)

// main initializes signal traps, creates world state, starts the gRPC server, and executes the game loop.
func main() {
	fmt.Println("game service starting…")

	// Set up signal listening for graceful shutdown upon SIGINT (Ctrl+C) or SIGTERM (systemd/k8s/docker).
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// Initialize the authoritative global world state.
	worldState := world.NewWorldState()

	// Bootstrap gRPC server listener with reflection and graceful shutdown wiring.
	grpcServer, lis, err := netcodeNet.StartGRPC(ctx, worldState, "")
	if err != nil {
		log.Fatalf("failed to bootstrap gRPC server: %v", err)
	}

	go func() {
		if err := grpcServer.Serve(lis); err != nil && err != grpc.ErrServerStopped {
			log.Printf("gRPC server encountered error: %v\n", err)
		}
	}()

	// Instantiate the tick loop with simulation processing and snapshot broadcasting.
	loop := tick.NewLoop(func(t int64) {
		worldState.Mu.Lock()
		worldState.Tick = t
		worldState.Mu.Unlock()

		// Execute authoritative simulation tick (movement, bullets, lag-comp hit detection, history recording).
		physics.SimulateWorld(worldState, physics.TickDuration)

		// Broadcast authoritative snapshots to all connected players.
		netcodeNet.BroadcastSnapshots(worldState)

		// Print periodic tick diagnostic to stdout once per second (every 64 ticks).
		if t%physics.TickRate == 0 {
			fmt.Printf("tick: %d\n", t)
		}
	})

	// Start the tick loop synchronously until context cancellation is received.
	loop.Start(ctx)

	fmt.Println("game service shutting down…")
}
