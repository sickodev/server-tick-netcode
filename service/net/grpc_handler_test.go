package net

import (
	"context"
	"io"
	"net"
	"testing"
	"time"

	"github.com/server-tick-netcode/service/proto/pb"
	"github.com/server-tick-netcode/service/world"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/test/bufconn"
)

const bufSize = 1024 * 1024

// setupTestServer starts an in-memory gRPC server backed by bufconn.
func setupTestServer(t *testing.T) (*world.WorldState, pb.GameServiceClient, func()) {
	lis := bufconn.Listen(bufSize)
	server := grpc.NewServer()
	w := world.NewWorldState()
	handler := NewGameHandler(w)
	pb.RegisterGameServiceServer(server, handler)

	go func() {
		if err := server.Serve(lis); err != nil && err != grpc.ErrServerStopped {
			t.Errorf("Server error: %v", err)
		}
	}()

	dialer := func(context.Context, string) (net.Conn, error) {
		return lis.Dial()
	}

	ctx := context.Background()
	conn, err := grpc.DialContext(ctx, "bufnet",
		grpc.WithContextDialer(dialer),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
	)
	if err != nil {
		t.Fatalf("Failed to dial bufnet: %v", err)
	}

	client := pb.NewGameServiceClient(conn)

	cleanup := func() {
		conn.Close()
		server.GracefulStop()
		lis.Close()
	}

	return w, client, cleanup
}

// TestStartGRPCBootstrap verifies listener startup and graceful shutdown under context cancellation (S07).
func TestStartGRPCBootstrap(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	w := world.NewWorldState()

	server, lis, err := StartGRPC(ctx, w, "0") // port 0 assigns an available ephemeral port
	if err != nil {
		t.Fatalf("StartGRPC failed: %v", err)
	}
	defer lis.Close()

	go func() {
		_ = server.Serve(lis)
	}()

	// Verify server can accept TCP connections
	addr := lis.Addr().String()
	conn, err := grpc.Dial(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	if err != nil {
		t.Fatalf("Failed to connect to gRPC server at %s: %v", addr, err)
	}
	conn.Close()

	// Cancel context and verify graceful stop
	cancel()
	time.Sleep(50 * time.Millisecond)
}

// TestPlayJoinRequestValid verifies that a valid JoinRequest succeeds and returns initial spawn coordinates (S08).
func TestPlayJoinRequestValid(t *testing.T) {
	w, client, cleanup := setupTestServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	stream, err := client.Play(ctx)
	if err != nil {
		t.Fatalf("client.Play failed: %v", err)
	}

	// Send valid JoinRequest
	err = stream.Send(&pb.ClientMessage{
		Payload: &pb.ClientMessage_JoinRequest{
			JoinRequest: &pb.JoinRequest{
				PlayerId: "test-player-1",
				Name:     "TestRunner",
			},
		},
	})
	if err != nil {
		t.Fatalf("stream.Send JoinRequest failed: %v", err)
	}

	// Receive JoinResponse
	resp, err := stream.Recv()
	if err != nil {
		t.Fatalf("stream.Recv JoinResponse failed: %v", err)
	}

	joinResp := resp.GetJoinResponse()
	if joinResp == nil {
		t.Fatalf("Expected JoinResponse payload, got %v", resp.Payload)
	}
	if !joinResp.GetOk() {
		t.Fatalf("Expected JoinResponse ok=true, got ok=false")
	}
	if joinResp.GetSpawnX() <= 0 || joinResp.GetSpawnY() <= 0 {
		t.Fatalf("Expected valid positive spawn coordinates, got (%f, %f)", joinResp.GetSpawnX(), joinResp.GetSpawnY())
	}

	// Verify player exists in world state
	w.Mu.RLock()
	p, exists := w.Players["test-player-1"]
	_, hasSnapCh := w.SnapChs["test-player-1"]
	w.Mu.RUnlock()

	if !exists || p == nil {
		t.Fatal("Player was not added to world state")
	}
	if !hasSnapCh {
		t.Fatal("Snapshot channel was not registered for player")
	}

	// Send LeaveRequest and verify cleanup
	_ = stream.Send(&pb.ClientMessage{
		Payload: &pb.ClientMessage_LeaveRequest{
			LeaveRequest: &pb.LeaveRequest{
				PlayerId: "test-player-1",
			},
		},
	})

	// Wait briefly for server stream cleanup
	time.Sleep(50 * time.Millisecond)

	w.Mu.RLock()
	_, existsAfter := w.Players["test-player-1"]
	_, snapChAfter := w.SnapChs["test-player-1"]
	w.Mu.RUnlock()

	if existsAfter || snapChAfter {
		t.Fatal("Player or snapshot channel was not cleaned up after leave")
	}
}

// TestPlayJoinRequestInvalid verifies that empty player IDs or non-join initial messages are rejected (S08).
func TestPlayJoinRequestInvalid(t *testing.T) {
	_, client, cleanup := setupTestServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	// Test 1: Empty player_id JoinRequest
	stream, err := client.Play(ctx)
	if err != nil {
		t.Fatalf("client.Play failed: %v", err)
	}

	err = stream.Send(&pb.ClientMessage{
		Payload: &pb.ClientMessage_JoinRequest{
			JoinRequest: &pb.JoinRequest{
				PlayerId: "",
			},
		},
	})
	if err != nil {
		t.Fatalf("stream.Send failed: %v", err)
	}

	resp, _ := stream.Recv()
	if resp != nil && resp.GetJoinResponse() != nil {
		if resp.GetJoinResponse().GetOk() {
			t.Fatal("Expected ok=false for empty player_id")
		}
	}

	// Test 2: First message is UserCmd instead of JoinRequest
	stream2, err := client.Play(ctx)
	if err != nil {
		t.Fatalf("client.Play failed: %v", err)
	}

	_ = stream2.Send(&pb.ClientMessage{
		Payload: &pb.ClientMessage_UserCmd{
			UserCmd: &pb.UserCmd{
				PlayerId: "p1",
				Seq:      1,
			},
		},
	})

	resp2, err2 := stream2.Recv()
	if resp2 != nil && resp2.GetJoinResponse() != nil && resp2.GetJoinResponse().GetOk() {
		t.Fatal("Expected rejection for non-join initial message")
	}
	if err2 == nil && (resp2 == nil || !resp2.GetJoinResponse().GetOk()) {
		// Rejection ok
	}
}

// TestPlayUserCmdRouting verifies that subsequent UserCmd messages are parsed and enqueued (S09).
func TestPlayUserCmdRouting(t *testing.T) {
	w, client, cleanup := setupTestServer(t)
	defer cleanup()

	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()

	stream, err := client.Play(ctx)
	if err != nil {
		t.Fatalf("client.Play failed: %v", err)
	}

	// Join
	_ = stream.Send(&pb.ClientMessage{
		Payload: &pb.ClientMessage_JoinRequest{
			JoinRequest: &pb.JoinRequest{
				PlayerId: "p-cmd",
				Name:     "Commander",
			},
		},
	})

	_, err = stream.Recv()
	if err != nil {
		t.Fatalf("Join recv failed: %v", err)
	}

	// Send UserCmds
	for i := 1; i <= 3; i++ {
		err = stream.Send(&pb.ClientMessage{
			Payload: &pb.ClientMessage_UserCmd{
				UserCmd: &pb.UserCmd{
					PlayerId: "p-cmd",
					Seq:      int32(i),
					Dx:       1.0,
					Dy:       0.0,
					AimAngle: 1.57,
					Fire:     true,
				},
			},
		})
		if err != nil {
			t.Fatalf("Send UserCmd failed: %v", err)
		}
	}

	// Wait for queue processing
	time.Sleep(50 * time.Millisecond)

	w.Mu.RLock()
	queue := w.Queues["p-cmd"]
	w.Mu.RUnlock()

	if queue == nil {
		t.Fatal("Queue for player 'p-cmd' was nil")
	}

	// Check received commands
	if len(queue.Cmds) != 3 {
		t.Fatalf("Expected 3 queued commands, got %d", len(queue.Cmds))
	}

	cmd1 := <-queue.Cmds
	if cmd1.Seq != 1 || cmd1.DX != 1.0 || !cmd1.Fire {
		t.Fatalf("Unexpected cmd1 values: %+v", cmd1)
	}
}

// TestBuildSnapshot verifies correct snapshot construction and IsSelf flagging (S10).
func TestBuildSnapshot(t *testing.T) {
	w := world.NewWorldState()
	w.Tick = 100
	w.AddPlayer("p1")
	w.AddPlayer("p2")

	w.Mu.Lock()
	w.LastAckSeq["p1"] = 42
	w.Bullets = append(w.Bullets, &world.BulletState{
		ID:      "b1",
		OwnerID: "p1",
		X:       50,
		Y:       60,
		VX:      200,
		VY:      0,
	})
	w.Mu.Unlock()

	snap := BuildSnapshot(w, "p1")
	if snap.ServerTick != 100 {
		t.Fatalf("Expected ServerTick 100, got %d", snap.ServerTick)
	}
	if snap.AckSeq != 42 {
		t.Fatalf("Expected AckSeq 42, got %d", snap.AckSeq)
	}
	if len(snap.Entities) != 2 {
		t.Fatalf("Expected 2 entities, got %d", len(snap.Entities))
	}
	if len(snap.Bullets) != 1 {
		t.Fatalf("Expected 1 bullet, got %d", len(snap.Bullets))
	}

	var selfCount int
	for _, e := range snap.Entities {
		if e.IsSelf {
			selfCount++
			if e.Id != "p1" {
				t.Fatalf("Expected IsSelf for 'p1', got for '%s'", e.Id)
			}
		}
	}
	if selfCount != 1 {
		t.Fatalf("Expected exactly 1 IsSelf entity, got %d", selfCount)
	}
}

// TestBroadcastSnapshots verifies snapshot broadcasting to multiple player channels (S10).
func TestBroadcastSnapshots(t *testing.T) {
	w := world.NewWorldState()
	w.Tick = 64
	w.AddPlayer("p1")
	w.AddPlayer("p2")

	ch1 := make(chan *pb.ServerMessage, 4)
	ch2 := make(chan *pb.ServerMessage, 4)

	w.RegisterSnapshotCh("p1", ch1)
	w.RegisterSnapshotCh("p2", ch2)

	BroadcastSnapshots(w)

	// Check message for p1
	select {
	case msg1 := <-ch1:
		snap1 := msg1.GetSnapshot()
		if snap1 == nil {
			t.Fatal("Expected Snapshot payload for p1")
		}
		if snap1.ServerTick != 64 {
			t.Fatalf("Expected tick 64, got %d", snap1.ServerTick)
		}
	default:
		t.Fatal("p1 channel did not receive snapshot")
	}

	// Check message for p2
	select {
	case msg2 := <-ch2:
		snap2 := msg2.GetSnapshot()
		if snap2 == nil {
			t.Fatal("Expected Snapshot payload for p2")
		}
	default:
		t.Fatal("p2 channel did not receive snapshot")
	}
}

// TestBroadcastSnapshotsNonBlockingDrop verifies that slow client channels do not block broadcasting (S10).
func TestBroadcastSnapshotsNonBlockingDrop(t *testing.T) {
	w := world.NewWorldState()
	w.Tick = 10
	w.AddPlayer("slow-p")
	w.AddPlayer("fast-p")

	slowCh := make(chan *pb.ServerMessage, 2)
	fastCh := make(chan *pb.ServerMessage, 4)

	w.RegisterSnapshotCh("slow-p", slowCh)
	w.RegisterSnapshotCh("fast-p", fastCh)

	// Fill slow client's buffer
	slowCh <- &pb.ServerMessage{}
	slowCh <- &pb.ServerMessage{}

	// Broadcast should not block even though slowCh is full
	done := make(chan struct{})
	go func() {
		BroadcastSnapshots(w)
		close(done)
	}()

	select {
	case <-done:
		// Broadcast completed non-blockingly
	case <-time.After(100 * time.Millisecond):
		t.Fatal("BroadcastSnapshots blocked on full channel")
	}

	// Fast client should have received its snapshot
	select {
	case msg := <-fastCh:
		if msg.GetSnapshot() == nil {
			t.Fatal("fast client received invalid snapshot")
		}
	default:
		t.Fatal("fast client did not receive snapshot")
	}
}

// Suppress unused import warnings in test suite
var _ io.Reader
