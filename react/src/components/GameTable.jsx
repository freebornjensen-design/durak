import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const CARD_PATH = '/cards';

const SUIT_MAP = { 'SPADES': 'SPADE', 'HEARTS': 'HEART', 'DIAMONDS': 'DIAMOND', 'CLUBS': 'CLUB' };

function cardSvgFile(card) {
  if (!card) return '';
  const suit = SUIT_MAP[card.suit] || card.suit;
  const rankMap = {
    'ACE': '1', 'TWO': '2', 'THREE': '3', 'FOUR': '4', 'FIVE': '5',
    'SIX': '6', 'SEVEN': '7', 'EIGHT': '8', 'NINE': '9', 'TEN': '10',
    'JACK': '11-JACK', 'QUEEN': '12-QUEEN', 'KING': '13-KING'
  };
  const rank = rankMap[card.rank] || card.rank;
  return CARD_PATH + '/' + suit + '-' + rank + '.svg';
}

const GameTable = ({ roomCode, playerName, onLeave }) => {
  const [game, setGame] = useState(null);
  const [myIndex, setMyIndex] = useState(-1);
  const [selectedCard, setSelectedCard] = useState(null);
  const [selectedDefenseTarget, setSelectedDefenseTarget] = useState(null);
  const [players, setPlayers] = useState([]);
  const [error, setError] = useState('');
  const [waitingForGame, setWaitingForGame] = useState(true);
  const [showDebug, setShowDebug] = useState(false);
  const [connected, setConnected] = useState(false);
  const stompClientRef = useRef(null);
  const myIndexRef = useRef(-1);

  // Parse state from server
  const handleStateUpdate = useCallback((data) => {
    setGame(data);
    if (data.players) {
      setPlayers(data.players);
      const idx = data.players.indexOf(playerName);
      if (idx !== -1) {
        setMyIndex(idx);
        myIndexRef.current = idx;
        setWaitingForGame(false);
      }
    }
  }, [playerName]);

  // Initial state fetch via REST
  const fetchInitialState = useCallback(async () => {
    try {
      const res = await fetch('/api/game/state/' + roomCode);
      if (res.ok) {
        const data = await res.json();
        handleStateUpdate(data);
      }
    } catch (e) { /* ignore */ }
  }, [roomCode, handleStateUpdate]);

  // Connect to STOMP
  useEffect(() => {
    fetchInitialState();

    const socket = new SockJS('/api/ws', null, { transports: ['websocket', 'xhr-streaming', 'xhr-polling'] });
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnected(true);
        // Subscribe to game state updates for this room
        client.subscribe('/topic/game/' + roomCode, (message) => {
          const data = JSON.parse(message.body);
          handleStateUpdate(data);
        });
        console.log('STOMP connected, subscribed to /topic/game/' + roomCode);
      },
      onDisconnect: () => {
        setConnected(false);
        console.log('STOMP disconnected');
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message']);
        setConnected(false);
      }
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
      }
    };
  }, [roomCode, fetchInitialState, handleStateUpdate]);

  // Send game action via STOMP
  const sendAction = useCallback((action, body) => {
    if (!stompClientRef.current || !stompClientRef.current.connected) {
      setError('Not connected to server');
      return;
    }
    const payload = { roomCode, playerIdx: myIndexRef.current, ...(body || {}) };
    stompClientRef.current.publish({
      destination: '/app/game/' + roomCode + '/' + action,
      body: JSON.stringify(payload)
    });
    setSelectedCard(null);
    setSelectedDefenseTarget(null);
    setError('');
  }, [roomCode]);

  const handleCardClick = (card, idx) => {
    if (!game || game.gameState === 'FINISHED' || myIndex === -1) return;
    if (game.gameState === 'ATTACKING' && game.attackerIdx === myIndex) {
      setSelectedCard(selectedCard === idx ? null : idx);
    } else if (game.gameState === 'DEFENDING' && game.defenderIdx === myIndex) {
      if (selectedDefenseTarget !== null) {
        sendAction('defend', { attackIdx: selectedDefenseTarget, rank: card.rank, suit: card.suit });
      } else {
        setSelectedCard(selectedCard === idx ? null : idx);
      }
    } else if (game.gameState === 'THROWING_IN' && game.defenderIdx !== myIndex) {
      setSelectedCard(selectedCard === idx ? null : idx);
    }
  };

  const handleAttackTableCard = (tableIdx) => {
    if (game && game.gameState === 'DEFENDING' && game.defenderIdx === myIndex) {
      setSelectedDefenseTarget(selectedDefenseTarget === tableIdx ? null : tableIdx);
    }
  };

  if (waitingForGame) {
    return (
      <div className="game-table-container">
        <div className="game-header">
          <h2>&#9824; DURAK &#9829;</h2>
          <span className="room-code">{roomCode}</span>
          <button className="leave-btn" onClick={onLeave}>Leave</button>
          <button className="leave-btn" onClick={() => setShowDebug(!showDebug)} style={{marginLeft: "8px"}}>{showDebug ? "Hide Debug" : "Debug"}</button>
        </div>
        <div className="loading" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '80vh', gap: '1rem' }}>
          <div style={{ fontSize: '1.5rem' }}>Waiting for game to start...</div>
          <div style={{ fontSize: '0.9rem', color: 'var(--text-dim)' }}>Players: {players.length > 0 ? players.join(', ') : 'none yet'}</div>
          <div className="lobby-cards" style={{ marginTop: '2rem' }}>
            <img src="/cards/HEART-1.svg" className="lobby-card" alt="" />
            <img src="/cards/SPADE-13-KING.svg" className="lobby-card" alt="" />
            <img src="/cards/DIAMOND-12-QUEEN.svg" className="lobby-card" alt="" />
            <img src="/cards/CLUB-11-JACK.svg" className="lobby-card" alt="" />
          </div>
        </div>
      </div>
    );
  }

  if (!game) return <div className="loading">Loading game...</div>;

  const myHand = game.hands && game.hands[myIndex] ? game.hands[myIndex] : [];
  const isMyTurn = game.gameState === 'ATTACKING' && game.attackerIdx === myIndex;
  const isMyDefense = game.gameState === 'DEFENDING' && game.defenderIdx === myIndex;
  const canThrowIn = game.gameState === 'THROWING_IN' && game.defenderIdx !== myIndex;

  const getPlayerClass = (idx) => {
    if (game.gameState === 'FINISHED' && game.foolIdx === idx) return 'fool';
    if (idx === game.defenderIdx) return 'defender';
    if (idx === game.attackerIdx) return 'active';
    return '';
  };

  return (
    <div className="game-table-container">
      <div className="game-header">
        <h2>&#9824; DURAK &#9829;</h2>
        <span className="room-code">{roomCode}</span>
        <span className={'ws-badge ' + (connected ? 'ws-connected' : 'ws-disconnected')}>
          {connected ? 'Live' : 'Reconnecting...'}
        </span>
        <button className="leave-btn" onClick={onLeave}>Leave</button>
        <button className="leave-btn" onClick={() => setShowDebug(!showDebug)} style={{marginLeft: "8px"}}>{showDebug ? "Hide Debug" : "Debug"}</button>
      </div>

      {game.lastAction && <div className="last-action">{game.lastAction}</div>}

      <div className="game-table">
        <div className="players-row" style={{ marginTop: '2rem' }}>
          {players.map((name, idx) => (
            idx !== myIndex ? (
              <div key={idx} className={'player-tag ' + getPlayerClass(idx)}>
                <span>{name}</span>
                <span className="player-card-count">{game.hands && game.hands[idx] ? game.hands[idx].length : 0} cards</span>
              </div>
            ) : null
          ))}
        </div>

        <div className="table-center">
          {game.gameState === 'FINISHED' ? (
            <div className="game-over-overlay">
              <div className="game-over-card">
                <h2 style={{ color: game.foolIdx === myIndex ? 'var(--red)' : 'var(--gold)' }}>
                  {game.foolIdx === myIndex ? 'You are the FOOL! :D' : 'Victory! :)'}
                </h2>
                <p>{game.lastAction}</p>
                <button className="play-again-btn" onClick={onLeave}>To Lobby</button>
              </div>
            </div>
          ) : null}

          <div className="table-cards">
            {game.tableCards && game.tableCards.map((tc, idx) => (
              <div key={idx} className={"table-card-pair" + (selectedDefenseTarget === idx ? " selected" : "")} onClick={() => handleAttackTableCard(idx)}>
                <img src={cardSvgFile(tc.attackCard)} className="card-small" alt="attack card" />
                {tc.defenseCard && (
                  <img src={cardSvgFile(tc.defenseCard)} className="card-small defense-card" alt="defense card" />
                )}
              </div>
            ))}
          </div>

          <div className="table-info">
            <div className="trump-indicator">
              <span>Trump:</span>
              <img src={cardSvgFile({ suit: game.trumpSuit, rank: 'ACE' })} className="trump-card" alt="trump suit" />
            </div>
            <span className="deck-count">Deck: {game.deckSize} cards</span>
            <span className="deck-count">Turn #{game.turnNumber}</span>
          </div>
        </div>

        <div className="players-row">
          <div className={'player-tag ' + getPlayerClass(myIndex)}>
            <span>{playerName} (you)</span>
            <span className="player-card-count">{myHand.length} cards</span>
          </div>
        </div>
      </div>

      <div className="player-hand">
        {myHand.map((card, idx) => (
          <div key={idx} className="card-wrapper" onClick={() => handleCardClick(card, idx)}>
            <img
              src={cardSvgFile(card)}
              className={'card-in-hand' + (selectedCard === idx ? ' selected' : '')}
              alt={card.rank + ' ' + card.suit}
            />
          </div>
        ))}
      </div>

      <div className="action-bar">
        {game.gameState === 'ATTACKING' && game.attackerIdx === myIndex && (
          <button className="action-btn action-btn-throw" disabled={selectedCard === null} onClick={() => {
            const card = myHand[selectedCard];
            sendAction('attack', { rank: card.rank, suit: card.suit });
          }}>
            Attack
          </button>
        )}

        {game.gameState === 'DEFENDING' && game.defenderIdx === myIndex && (
          <>
            <button className="action-btn action-btn-beat" disabled={selectedCard === null || selectedDefenseTarget === null} onClick={() => {
              const card = myHand[selectedCard];
              sendAction('defend', { attackIdx: selectedDefenseTarget, rank: card.rank, suit: card.suit });
            }}>
              Beat
            </button>
            <button className="action-btn action-btn-take" onClick={() => sendAction('take', {})}>
              Take
            </button>
          </>
        )}

        {game.gameState === 'THROWING_IN' && canThrowIn && (
          <button className="action-btn action-btn-throw" disabled={selectedCard === null} onClick={() => {
            const card = myHand[selectedCard];
            sendAction('throw', { rank: card.rank, suit: card.suit });
          }}>
            Throw
          </button>
        )}

        {game.gameState === 'THROWING_IN' && game.defenderIdx !== myIndex && (
          <button className="action-btn action-btn-pass" onClick={() => sendAction('pass', {})}>
            Pass
          </button>
        )}
      </div>

      {showDebug && game && (
        <div className="debug-panel">
          <div className="debug-header">[DEBUG] Game State</div>
          <div className="debug-section">
            <strong>State:</strong> {game.gameState} | <strong>Turn:</strong> {game.turnNumber} |
            <strong>Attacker:</strong> {game.attackerIdx} | <strong>Defender:</strong> {game.defenderIdx} |
            <strong>Deck:</strong> {game.deckSize} | <strong>Table:</strong> {(game.tableCards || []).length}
          </div>
          <div className="debug-section">
            <strong>Players:</strong> {game.players && game.players.map((p, i) => (
              <span key={i} style={{marginRight: 8}}>{p + (i === game.attackerIdx ? " (A)" : "") + (i === game.defenderIdx ? " (D)" : "")}</span>
            ))}
          </div>
          <div className="debug-log-list">
            {(game.actionLog || []).slice().reverse().map((entry, i) => (
              <div key={i} className="debug-log-entry">{entry}</div>
            ))}
          </div>
        </div>
      )}
      {error && <div style={{ textAlign: 'center', color: '#ff6b6b', padding: '0.5rem', fontSize: '0.9rem' }}>{error}</div>}
    </div>
  );
};

export default GameTable;
