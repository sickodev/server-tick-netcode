// Package net implements the gRPC networking layer for the authoritative game server.
// It manages client streaming connections, input routing, and tick snapshot broadcasting.
package net

import (
	"context"
	"fmt"
	"log"
	"net"
	"os"
	"sync/atomic"
	"time"

	"github.com/server-tick-netcode/service/proto/pb"
	"github.com/server-tick-netcode/service/world"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/reflection"
	"google.golang.org/grpc/status"
)

const (
	// DefaultGRPCPort is the fallback port when GRPC_PORT is not specified in the environment.
	DefaultGRPCPort = "9090"

	// SnapshotBufferCap is the capacity of the snapshot channel for each connected player session.
	SnapshotBufferCap = 4
)

// GameHandler implements the pb.GameServiceServer interface for bidirectional client streaming.
type GameHandler struct {
	pb.UnimplementedGameServiceServer
	world           *world.WorldState
	droppedCmdCount atomic.Uint64
}

// NewGameHandler allocates a new GameHandler wired to the specified authoritative world state.
func NewGameHandler(w *world.WorldState) *GameHandler {
	return &GameHandler{
		world: w,
	}
}

// StartGRPC bootstraps the gRPC server, registers the GameService and reflection,
// and starts serving requests. It listens for context cancellation to initiate GracefulStop.
func StartGRPC(ctx context.Context, w *world.WorldState, port string) (*grpc.Server, net.Listener, error) {
	if port == "" {
		port = os.Getenv("PORT")
		if port == "" {
			port = os.Getenv("GRPC_PORT")
			if port == "" {
				port = DefaultGRPCPort
			}
		}
	}

	lis, err := net.Listen("tcp", ":"+port)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to listen on port %s: %w", port, err)
	}

	server := grpc.NewServer()
	handler := NewGameHandler(w)
	pb.RegisterGameServiceServer(server, handler)
	reflection.Register(server)

	// Start periodic dropped command reporter (logged once per second if dropped > 0)
	go handler.startDroppedCmdReporter(ctx)

	go func() {
		<-ctx.Done()
		server.GracefulStop()
	}()

	return server, lis, nil
}

// startDroppedCmdReporter logs the number of dropped client commands once per second if non-zero.
func (h *GameHandler) startDroppedCmdReporter(ctx context.Context) {
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			dropped := h.droppedCmdCount.Swap(0)
			if dropped > 0 {
				log.Printf("[dropped-cmds] %d commands dropped in the last second\n", dropped)
			}
		}
	}
}

// Play handles the bidirectional streaming connection for an active player session.
// It enforces the initial JoinRequest handshake, routes UserCmd inputs to the player's queue,
// and transmits tick snapshots via a dedicated buffered channel.
func (h *GameHandler) Play(stream pb.GameService_PlayServer) error {
	// 1. Initial message validation — must be a JoinRequest with non-empty player_id.
	firstMsg, err := stream.Recv()
	if err != nil {
		return err
	}

	joinReq := firstMsg.GetJoinRequest()
	if joinReq == nil || joinReq.GetPlayerId() == "" {
		_ = stream.Send(&pb.ServerMessage{
			Payload: &pb.ServerMessage_JoinResponse{
				JoinResponse: &pb.JoinResponse{
					Ok: false,
				},
			},
		})
		return status.Errorf(codes.InvalidArgument, "first message must be a valid JoinRequest with non-empty player_id")
	}

	playerID := joinReq.GetPlayerId()

	// 2. Add player to the authoritative world simulation.
	player := h.world.AddPlayer(playerID)
	log.Printf("[join] player %s spawned at (%.2f, %.2f)\n", playerID, player.X, player.Y)

	// 3. Acknowledge the join with spawn coordinates.
	if err := stream.Send(&pb.ServerMessage{
		Payload: &pb.ServerMessage_JoinResponse{
			JoinResponse: &pb.JoinResponse{
				Ok:     true,
				SpawnX: float32(player.X),
				SpawnY: float32(player.Y),
			},
		},
	}); err != nil {
		h.world.RemovePlayer(playerID)
		return err
	}

	// 4. Allocate and register a dedicated snapshot channel for this player session.
	snapCh := make(chan *pb.ServerMessage, SnapshotBufferCap)
	h.world.RegisterSnapshotCh(playerID, snapCh)

	// Stream cleanup on exit: unregister channel, remove player from world, log disconnect.
	defer func() {
		h.world.UnregisterSnapshotCh(playerID)
		h.world.RemovePlayer(playerID)
		log.Printf("[leave] player %s disconnected\n", playerID)
	}()

	// Spawn sender goroutine to stream snapshots to the client.
	sendErrCh := make(chan error, 1)
	sendCtx, cancelSend := context.WithCancel(stream.Context())
	defer cancelSend()

	go func() {
		for {
			select {
			case <-sendCtx.Done():
				return
			case snapMsg, ok := <-snapCh:
				if !ok {
					return
				}
				if err := stream.Send(snapMsg); err != nil {
					select {
					case sendErrCh <- err:
					default:
					}
					return
				}
			}
		}
	}()

	// 5. Main receive loop: process client messages (UserCmd, LeaveRequest) until stream termination.
	for {
		select {
		case err := <-sendErrCh:
			return err
		default:
		}

		msg, err := stream.Recv()
		if err != nil {
			return err
		}

		// Handle graceful client leave request
		if leaveReq := msg.GetLeaveRequest(); leaveReq != nil {
			return nil
		}

		// Handle user input command
		if cmd := msg.GetUserCmd(); cmd != nil {
			userCmd := world.UserCmd{
				Seq:      uint32(cmd.GetSeq()),
				DX:       float64(cmd.GetDx()),
				DY:       float64(cmd.GetDy()),
				AimAngle: float64(cmd.GetAimAngle()),
				Fire:     cmd.GetFire(),
			}

			if !h.world.EnqueueCmd(playerID, userCmd) {
				h.droppedCmdCount.Add(1)
			}
		}
	}
}

// BuildSnapshot constructs an authoritative game state Snapshot for a specific player.
// It sets IsSelf = true for the receiving player and includes the highest acknowledged input sequence.
func BuildSnapshot(w *world.WorldState, forPlayer string) *pb.Snapshot {
	w.Mu.RLock()
	defer w.Mu.RUnlock()

	snap := &pb.Snapshot{
		ServerTick: w.Tick,
		AckSeq:     int32(w.LastAckSeq[forPlayer]),
		Entities:   make([]*pb.EntityState, 0, len(w.Players)),
		Bullets:    make([]*pb.BulletState, 0, len(w.Bullets)),
	}

	for id, p := range w.Players {
		snap.Entities = append(snap.Entities, &pb.EntityState{
			Id:     id,
			X:      float32(p.X),
			Y:      float32(p.Y),
			Angle:  float32(p.Angle),
			Health: int32(p.Health),
			IsSelf: id == forPlayer,
		})
	}

	for _, b := range w.Bullets {
		snap.Bullets = append(snap.Bullets, &pb.BulletState{
			Id:      b.ID,
			OwnerId: b.OwnerID,
			X:       float32(b.X),
			Y:       float32(b.Y),
			Vx:      float32(b.VX),
			Vy:      float32(b.VY),
		})
	}

	return snap
}

// BroadcastSnapshots builds and non-blockingly dispatches a snapshot to every connected player.
// Slow clients whose snapshot channels are full are dropped immediately to protect tick cadence.
func BroadcastSnapshots(w *world.WorldState) {
	w.Mu.RLock()
	defer w.Mu.RUnlock()

	if len(w.SnapChs) == 0 {
		return
	}

	// Pre-build common entity templates and bullet list under the read lock
	rawEntities := make([]*pb.EntityState, 0, len(w.Players))
	for id, p := range w.Players {
		rawEntities = append(rawEntities, &pb.EntityState{
			Id:     id,
			X:      float32(p.X),
			Y:      float32(p.Y),
			Angle:  float32(p.Angle),
			Health: int32(p.Health),
		})
	}

	bullets := make([]*pb.BulletState, 0, len(w.Bullets))
	for _, b := range w.Bullets {
		bullets = append(bullets, &pb.BulletState{
			Id:      b.ID,
			OwnerId: b.OwnerID,
			X:       float32(b.X),
			Y:       float32(b.Y),
			Vx:      float32(b.VX),
			Vy:      float32(b.VY),
		})
	}

	for playerID, ch := range w.SnapChs {
		playerEntities := make([]*pb.EntityState, len(rawEntities))
		for i, e := range rawEntities {
			playerEntities[i] = &pb.EntityState{
				Id:     e.Id,
				X:      e.X,
				Y:      e.Y,
				Angle:  e.Angle,
				Health: e.Health,
				IsSelf: e.Id == playerID,
			}
		}

		snap := &pb.Snapshot{
			ServerTick: w.Tick,
			AckSeq:     int32(w.LastAckSeq[playerID]),
			Entities:   playerEntities,
			Bullets:    bullets,
		}

		msg := &pb.ServerMessage{
			Payload: &pb.ServerMessage_Snapshot{
				Snapshot: snap,
			},
		}

		select {
		case ch <- msg:
		default:
			// Non-blocking drop on full buffer to ensure slow clients never block the tick loop
		}
	}
}
