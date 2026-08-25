import React, { useEffect, useRef } from 'react';
import { ARENA_W, ARENA_H } from '../game/constants';
import { GameEngine } from '../game/GameEngine';

/**
 * GameCanvas React component.
 *
 * Mounts the primary 1200x800 HTML5 canvas viewport and manages the lifecycle of the GameEngine.
 * On mount, initializes and starts the game loop; on unmount, halts the loop and cleans up resources.
 *
 * @returns JSX Element containing the styled canvas container and game canvas.
 */
export const GameCanvas: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) {
      return;
    }

    // Initialize the core game engine on canvas mount
    const engine = new GameEngine(canvas);
    engine.start();

    // Clean up and halt the game loop when the component unmounts
    return () => {
      engine.stop();
    };
  }, []);

  return (
    <div className="game-container">
      <canvas
        id="game-canvas"
        ref={canvasRef}
        width={ARENA_W}
        height={ARENA_H}
        className="game-canvas"
      />
    </div>
  );
};

export default GameCanvas;
