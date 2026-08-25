import React from 'react';
import { GameCanvas } from './components/GameCanvas';
import './App.css';

/**
 * Root Application component.
 *
 * Renders the top-level layout container hosting the multiplayer game canvas.
 *
 * @returns JSX Element rendering the GameCanvas.
 */
export const App: React.FC = () => {
  return (
    <main className="app-root">
      <GameCanvas />
    </main>
  );
};

export default App;
