# Durak Online ♠️♥️♣️♦️

Online multiplayer card game "Fool" (Durak) with WebSocket real-time updates, AI opponents, and voice chat.

---

## Stack

| Layer | Technology |
|-------|-----------|
| **Frontend** | React 19 + Vite + STOMP WebSocket (SockJS) |
| **Backend** | Java 21 + Spring Boot + Hibernate |
| **Database** | PostgreSQL 16 (Docker) |
| **Message Queue** | RabbitMQ 4 (Docker) |
| **Web Server** | Nginx (reverse proxy + static) |
| **Hosting** | Ubuntu server via systemd |

---

## Project Structure

```
durak/
├── react/                          # Frontend (React + Vite)
│   ├── src/
│   │   ├── components/
│   │   │   ├── GameTable.jsx       # Main game UI: table, hand, actions, STOMP
│   │   │   └── Lobby.jsx           # Room create/join screen
│   │   ├── styles/
│   │   │   └── global.css          # All styles (lobby, game table, cards, animations)
│   │   ├── App.jsx                 # Root component (lobby ↔ game routing)
│   │   └── main.jsx                # Entry point
│   ├── public/
│   │   └── cards/                  # SVG card images (rank-suit naming)
│   │       ├── HEART-1.svg         # Ace of Hearts
│   │       ├── SPADE-13-KING.svg   # King of Spades
│   │       ├── backs.svg           # Card back (used for deck + opponents)
│   │       └── ...
│   ├── index.html
│   ├── package.json
│   └── vite.config.js              # Vite config with globalThis polyfill
│
├── java/                           # Backend (Spring Boot)
│   └── src/main/java/com/durak/
│       ├── controller/
│       │   ├── GameController.java        # REST endpoints (create, join, state)
│       │   └── GameWebSocketController.java # STOMP handlers (attack, defend, throw, pass, take)
│       ├── service/
│       │   └── GameService.java           # Game logic orchestrator + AI turns
│       ├── game/
│       │   └── DurakEngine.java           # Core game engine (rules, deck, turns)
│       ├── model/
│       │   ├── Card.java                  # Card + Rank + Suit enums
│       │   ├── TableCard.java             # Attack/defense card pair
│       │   └── GameRoom.java              # JPA entity
│       ├── repository/
│       │   └── GameRoomRepository.java    # DB access
│       └── config/
│           └── WebSocketConfig.java       # STOMP endpoint + broker config
│
├── deploy.sh                       # Auto-deploy script (webhook target)
├── README.md                       # This file
```

---

## Key Architecture Decisions

### WebSocket (STOMP) over REST polling
- Frontend connects via `SockJS → /api/ws` → STOMP
- Subscribes to `/topic/game/{roomCode}` for real-time state
- Sends actions via `/app/game/{roomCode}/{action}`
- REST is used only for room create/join

### AI Cascade System
When a human acts, Java processes AI turns in a cascade loop (max 100 depth):
1. AI attacker attacks
2. AI defender defends or takes
3. If throw-in, AI throwers throw or pass
4. Loop repeats until the turn reaches the human or game state stabilizes

### Visual Deck
- The deck visual shows 8 stacked card backs
- Stack thickness + fan spread scales with `deckSize / 36`
- Animated via CSS `cubic-bezier` transitions
- Last card gets a gold border when deck is non-empty

### Opponent Cards
- Opponents' card counts are displayed as actual card back images
- Up to 5 cards shown in a mini-fan; overflow shows "+X"

---

## Development

```bash
# Frontend
cd react
npm install
npm run dev              # Vite dev server on :5173

# Backend
cd java
mvn spring-boot:run      # Spring Boot on :8080

# Database (Docker)
docker start durak-pg durak-mq
```

## Deployment

```bash
# Manual deploy
cd /var/www/durak
./deploy.sh

# Auto-deploy: GitHub webhook → deploy.sh
```

## Environment

| Port | Service |
|------|---------|
| 4343 | Nginx (React static + API proxy) |
| 8080 | Spring Boot (Java backend) |
| 5432 | PostgreSQL (Docker) |
| 5672 | RabbitMQ (Docker) |
