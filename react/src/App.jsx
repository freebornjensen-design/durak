import React, { useState } from 'react';
import Lobby from './components/Lobby';
import GameTable from './components/GameTable';
import './styles/global.css';

function App() {
  const [gameState, setGameState] = useState({ screen: 'lobby', roomCode: null, playerName: '' });

  const handleJoinGame = (code, name) => {
    setGameState({ screen: 'table', roomCode: code, playerName: name });
  };

  const handleLeave = () => {
    setGameState({ screen: 'lobby', roomCode: null, playerName: '' });
  };

  return (
    <div className="app">
      {gameState.screen === 'lobby' ? (
        <Lobby onJoinGame={handleJoinGame} />
      ) : (
        <GameTable
          roomCode={gameState.roomCode}
          playerName={gameState.playerName}
          onLeave={handleLeave}
        />
      )}
    </div>
  );
}

export default App;
