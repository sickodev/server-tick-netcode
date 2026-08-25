// Package world defines core game models, simulation world state, and historical buffers.
package world

import (
	"sync"
)

// HistoryBufferSize is the fixed capacity of the circular history ring buffer.
// At 64Hz tick rate, 128 slots provide exactly 2.0 seconds of lag compensation history.
const HistoryBufferSize = 128

// HistoricalPlayerState contains a snapshot of a player's spatial and health state at a specific tick.
type HistoricalPlayerState struct {
	// ID is the unique player identifier.
	ID string
	// X is the horizontal position in pixels in the arena.
	X float64
	// Y is the vertical position in pixels in the arena.
	Y float64
	// Angle is the aim orientation in radians.
	Angle float64
	// Health is the player's hit point pool (0-100).
	Health int
	// Speed is the player's movement speed in pixels per second.
	Speed float64
}

// HistoricalFrame stores the authoritative snapshot of all active players at a single simulation tick.
type HistoricalFrame struct {
	// Valid indicates whether this ring buffer slot contains an active recorded tick.
	Valid bool
	// Tick is the authoritative simulation tick number for this snapshot frame.
	Tick int64
	// Players maps player IDs to their deep-copied historical states at this tick.
	Players map[string]*HistoricalPlayerState
}

// Clone creates an independent deep copy of a HistoricalPlayerState.
func (p *HistoricalPlayerState) Clone() *HistoricalPlayerState {
	if p == nil {
		return nil
	}
	return &HistoricalPlayerState{
		ID:     p.ID,
		X:      p.X,
		Y:      p.Y,
		Angle:  p.Angle,
		Health: p.Health,
		Speed:  p.Speed,
	}
}

// ClonePlayerToHistorical creates an independent deep copy of a PlayerState as a HistoricalPlayerState.
func ClonePlayerToHistorical(p *PlayerState) *HistoricalPlayerState {
	if p == nil {
		return nil
	}
	return &HistoricalPlayerState{
		ID:     p.ID,
		X:      p.X,
		Y:      p.Y,
		Angle:  p.Angle,
		Health: p.Health,
		Speed:  p.Speed,
	}
}

// ToPlayerState converts a HistoricalPlayerState to a live PlayerState pointer.
func (p *HistoricalPlayerState) ToPlayerState() *PlayerState {
	if p == nil {
		return nil
	}
	return &PlayerState{
		ID:     p.ID,
		X:      p.X,
		Y:      p.Y,
		Angle:  p.Angle,
		Health: p.Health,
		Speed:  p.Speed,
	}
}

// HistoryBuffer is a thread-safe, fixed-size circular ring buffer that retains past world states
// for server-side lag compensation raycasting. Memory footprint is strictly bounded to 128 slots.
type HistoryBuffer struct {
	// mu guards concurrent reads and writes to the ring buffer.
	mu sync.RWMutex
	// slots is the fixed-size array holding historical frames.
	slots [HistoryBufferSize]HistoricalFrame
	// latestTick is the most recent tick recorded in the buffer.
	latestTick int64
	// count is the total number of frames recorded since initialization.
	count int64
}

// NewHistoryBuffer constructs an empty, preallocated HistoryBuffer with HistoryBufferSize slots.
func NewHistoryBuffer() *HistoryBuffer {
	return &HistoryBuffer{
		latestTick: 0,
		count:      0,
	}
}

// Record inserts a deep-copied snapshot of the active players at the specified tick into the ring buffer.
// When the buffer capacity (128) is exceeded, older entries are safely overwritten in a circular manner.
// This method is thread-safe and acquires an exclusive write lock.
func (h *HistoryBuffer) Record(tick int64, players map[string]*PlayerState) {
	h.mu.Lock()
	defer h.mu.Unlock()

	slotIndex := int(tick % int64(HistoryBufferSize))
	if slotIndex < 0 {
		slotIndex += HistoryBufferSize
	}

	// Create deep copy of player map to isolate history from future live state mutations
	copiedPlayers := make(map[string]*HistoricalPlayerState, len(players))
	for id, p := range players {
		if p != nil {
			copiedPlayers[id] = ClonePlayerToHistorical(p)
		}
	}

	h.slots[slotIndex] = HistoricalFrame{
		Valid:   true,
		Tick:    tick,
		Players: copiedPlayers,
	}

	if tick > h.latestTick || h.count == 0 {
		h.latestTick = tick
	}
	h.count++
}

// StateAt retrieves the historical world frame for the exact requested tick.
// Returns nil if the tick is unrecorded, has been overwritten by the ring buffer wrap,
// or is in the future relative to the latest recorded tick.
// This method is thread-safe and acquires a read lock.
func (h *HistoryBuffer) StateAt(tick int64) *HistoricalFrame {
	h.mu.RLock()
	defer h.mu.RUnlock()

	if h.count == 0 || tick <= 0 {
		return nil
	}

	// If requested tick is beyond the latest recorded tick, return nil
	if tick > h.latestTick {
		return nil
	}

	// Calculate the oldest valid tick currently preserved in the ring buffer
	var earliestTick int64
	if h.count >= int64(HistoryBufferSize) {
		earliestTick = h.latestTick - int64(HistoryBufferSize) + 1
	} else {
		earliestTick = h.latestTick - h.count + 1
	}
	if earliestTick < 1 {
		earliestTick = 1
	}

	// If requested tick is older than our retention window, it has been overwritten or not recorded
	if tick < earliestTick {
		return nil
	}

	slotIndex := int(tick % int64(HistoryBufferSize))
	if slotIndex < 0 {
		slotIndex += HistoryBufferSize
	}

	slot := &h.slots[slotIndex]
	if !slot.Valid || slot.Tick != tick {
		return nil
	}

	// Return a defensive copy of the frame to prevent external mutations
	frameCopy := &HistoricalFrame{
		Valid:   slot.Valid,
		Tick:    slot.Tick,
		Players: make(map[string]*HistoricalPlayerState, len(slot.Players)),
	}
	for id, p := range slot.Players {
		frameCopy.Players[id] = p.Clone()
	}

	return frameCopy
}

// ClosestStateAt retrieves the historical world frame for the requested tick, clamping
// to the earliest available tick if the requested tick has expired, or the latest recorded tick.
// Returns nil only if the history buffer is completely empty.
// This method is thread-safe and acquires a read lock.
func (h *HistoryBuffer) ClosestStateAt(tick int64) *HistoricalFrame {
	h.mu.RLock()
	defer h.mu.RUnlock()

	if h.count == 0 {
		return nil
	}

	var earliestTick int64
	if h.count >= int64(HistoryBufferSize) {
		earliestTick = h.latestTick - int64(HistoryBufferSize) + 1
	} else {
		earliestTick = h.latestTick - h.count + 1
	}
	if earliestTick < 1 {
		earliestTick = 1
	}

	targetTick := tick
	if targetTick < earliestTick {
		targetTick = earliestTick
	}
	if targetTick > h.latestTick {
		targetTick = h.latestTick
	}

	slotIndex := int(targetTick % int64(HistoryBufferSize))
	if slotIndex < 0 {
		slotIndex += HistoryBufferSize
	}

	slot := &h.slots[slotIndex]
	if !slot.Valid {
		return nil
	}

	frameCopy := &HistoricalFrame{
		Valid:   slot.Valid,
		Tick:    slot.Tick,
		Players: make(map[string]*HistoricalPlayerState, len(slot.Players)),
	}
	for id, p := range slot.Players {
		frameCopy.Players[id] = p.Clone()
	}

	return frameCopy
}

// Bounds returns the earliest and latest valid tick numbers currently retained in the buffer.
// Returns (0, 0) if the buffer is empty.
func (h *HistoryBuffer) Bounds() (earliest int64, latest int64) {
	h.mu.RLock()
	defer h.mu.RUnlock()

	if h.count == 0 {
		return 0, 0
	}

	if h.count >= int64(HistoryBufferSize) {
		earliest = h.latestTick - int64(HistoryBufferSize) + 1
	} else {
		earliest = h.latestTick - h.count + 1
	}
	if earliest < 1 {
		earliest = 1
	}
	return earliest, h.latestTick
}
