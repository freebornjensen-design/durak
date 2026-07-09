import React, { useState } from 'react';

const Lobby = ({ onJoinGame }) => {
  const [name, setName] = useState('');
  const [roomCode, setRoomCode] = useState('');
  const [deckType, setDeckType] = useState(36);
  const [gameMode, setGameMode] = useState('throwin');
  const [maxPlayers, setMaxPlayers] = useState(4);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const createRoom = async () => {
    if (!name.trim()) { setError('Enter your name'); return; }
    setLoading(true); setError('');
    try {
      // Step 1: Create room
      const res = await fetch('/api/game/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ deckType, mode: gameMode, maxPlayers })
      });
      const data = await res.json();
      if (data.error) { setError(data.error); setLoading(false); return; }

      const code = data.roomCode;

      // Step 2: Join the room as creator
      await fetch('/api/game/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roomCode: code, playerName: name })
      });

      // Step 3: Wait a moment then start the game
      setTimeout(async () => {
        try {
          await fetch('/api/game/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ roomCode: code })
          });
        } catch (e) { /* ignore start errors */ }
        onJoinGame(code, name);
      }, 500);
    } catch (e) { setError('Connection error'); }
    finally { setLoading(false); }
  };

  const joinRoom = async () => {
    if (!name.trim()) { setError('Enter your name'); return; }
    if (!roomCode.trim()) { setError('Enter room code'); return; }
    setLoading(true); setError('');
    try {
      const code = roomCode.toUpperCase();
      const res = await fetch('/api/game/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ roomCode: code, playerName: name })
      });
      const data = await res.json();
      if (data.error) { setError(data.error); setLoading(false); return; }
      onJoinGame(code, name);
    } catch (e) { setError('Connection error'); }
    finally { setLoading(false); }
  };

  return (
    <div className="lobby">
      <div className="lobby-cards">
        <img src="/cards/HEART-1.svg" className="lobby-card" alt="" />
        <img src="/cards/SPADE-13-KING.svg" className="lobby-card" alt="" />
        <img src="/cards/DIAMOND-12-QUEEN.svg" className="lobby-card" alt="" />
        <img src="/cards/CLUB-11-JACK.svg" className="lobby-card" alt="" />
      </div>

      <h1 className="lobby-title">DURAK</h1>
      <p className="lobby-subtitle">Online card game with voice chat</p>

      <div className="lobby-form">
        <input
          className="lobby-input"
          placeholder="Your name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={20}
        />

        <div className="lobby-row">
          <select className="lobby-select" value={deckType} onChange={(e) => setDeckType(Number(e.target.value))}>
            <option value={36}>36 cards</option>
            <option value={52}>52 cards</option>
          </select>
          <select className="lobby-select" value={gameMode} onChange={(e) => setGameMode(e.target.value)}>
            <option value="throwin">Throw-in</option>
            <option value="transfer">Transfer</option>
          </select>
          <select className="lobby-select" value={maxPlayers} onChange={(e) => setMaxPlayers(Number(e.target.value))}>
            <option value={2}>2 players</option>
            <option value={3}>3 players</option>
            <option value={4}>4 players</option>
          </select>
        </div>

        <button className="lobby-btn lobby-btn-primary" onClick={createRoom} disabled={loading}>
          {loading ? 'Creating...' : 'Create Room'}
        </button>

        <div className="lobby-divider">OR</div>

        <input
          className="lobby-input"
          placeholder="Room Code"
          value={roomCode}
          onChange={(e) => setRoomCode(e.target.value.toUpperCase())}
          maxLength={6}
          style={{ textTransform: 'uppercase', letterSpacing: '3px', textAlign: 'center' }}
        />
        <button className="lobby-btn lobby-btn-secondary" onClick={joinRoom} disabled={loading}>
          {loading ? 'Joining...' : 'Join Room'}
        </button>

        {error && <div className="lobby-error">{error}</div>}
      </div>
    </div>
  );
};

export default Lobby;
