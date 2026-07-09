package com.durak.service;

import com.durak.game.DurakEngine;
import com.durak.model.Card;
import com.durak.model.GameRoom;
import com.durak.repository.GameRoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {
    @Autowired private GameRoomRepository roomRepo;
    private final Map<String, DurakEngine> engines = new ConcurrentHashMap<>();
    private final Map<String, List<String>> roomPlayers = new ConcurrentHashMap<>();

    public GameRoom createRoom(int deckType, String mode, int maxPlayers) {
        GameRoom room = new GameRoom();
        room.setRoomCode(generateCode());
        room.setDeckType(deckType);
        room.setGameMode(mode);
        room.setMaxPlayers(maxPlayers);
        roomRepo.save(room);
        roomPlayers.put(room.getRoomCode(), new ArrayList<>());
        return room;
    }

    public boolean joinRoom(String roomCode, String playerName) {
        GameRoom room = roomRepo.findByRoomCode(roomCode);
        if (room == null || room.isStarted()) return false;
        List<String> players = roomPlayers.get(roomCode);
        if (players == null || players.size() >= room.getMaxPlayers() || players.contains(playerName)) return false;
        players.add(playerName);
        room.setPlayerNames(new ArrayList<>(players));
        roomRepo.save(room);
        return true;
    }

    public GameRoom startGame(String roomCode) {
        GameRoom room = roomRepo.findByRoomCode(roomCode);
        List<String> players = roomPlayers.get(roomCode);
        if (room == null || players == null || players.isEmpty()) return null;

        // Auto-fill with AI players if not enough human players
        String[] aiNames = {"Alice", "Bob", "Charlie", "Diana", "Eve", "Frank"};
        int aiIdx = 0;
        while (players.size() < room.getMaxPlayers()) {
            String aiName = aiNames[aiIdx % aiNames.length] + "-AI";
            if (!players.contains(aiName)) {
                players.add(aiName);
            }
            aiIdx++;
        }
        room.setPlayerNames(new ArrayList<>(players));

        DurakEngine engine = new DurakEngine(room.getDeckType(), room.getGameMode(), players.size());
        engines.put(roomCode, engine);
        room.setStarted(true);
        room.setTrumpSuit(engine.getTrump().name());
        room.setCurrentAttacker(engine.getAttackerIdx());
        room.setCurrentDefender(engine.getDefenderIdx());
        room.setTurnNumber(engine.getTurnNumber());
        roomRepo.save(room);
        return room;
    }

    public DurakEngine getEngine(String roomCode) { return engines.get(roomCode); }
    public List<String> getPlayers(String roomCode) { return roomPlayers.get(roomCode); }

    public Map<String, Object> buildStateMap(String roomCode) {
        DurakEngine engine = engines.get(roomCode);
        if (engine == null) {
            List<String> players = roomPlayers.get(roomCode);
            Map<String, Object> waiting = new HashMap<>();
            waiting.put("gameState", "WAITING");
            waiting.put("players", players != null ? players : new ArrayList<>());
            return waiting;
        }
        Map<String, Object> state = new HashMap<>();
        state.put("gameState", engine.getGameState());
        state.put("attackerIdx", engine.getAttackerIdx());
        state.put("defenderIdx", engine.getDefenderIdx());
        state.put("trumpSuit", engine.getTrump().name());
        state.put("turnNumber", engine.getTurnNumber());
        state.put("deckSize", engine.getDeckSize());
        state.put("lastAction", engine.getLastAction());
        state.put("actionLog", engine.getActionLog());
        state.put("tableCards", engine.getTableCards());
        state.put("players", roomPlayers.get(roomCode));

        List<String> players = roomPlayers.get(roomCode);
        List<List<Map<String, Object>>> hands = new ArrayList<>();
        if (players != null) {
            for (int i = 0; i < players.size(); i++) {
                List<Map<String, Object>> hand = new ArrayList<>();
                if (engine.getHand(i) != null) {
                    for (var card : engine.getHand(i)) {
                        Map<String, Object> cm = new HashMap<>();
                        cm.put("rank", card.getRank().name());
                        cm.put("suit", card.getSuit().name());
                        hand.add(cm);
                    }
                }
                hands.add(hand);
            }
        }
        state.put("hands", hands);

        if ("FINISHED".equals(engine.getGameState())) {
            state.put("foolIdx", engine.getDefenderIdx());
        }
        return state;
    }

    public Map<String, Object> processAiTurn(String roomCode) {
        DurakEngine engine = engines.get(roomCode);
        if (engine == null) return null;
        List<String> players = roomPlayers.get(roomCode);
        if (players == null) return null;

        Map<String, Object> actions = new HashMap<>();
        String state = engine.getGameState();
        String currentState = state;

        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).endsWith("-AI")) {
                if ("ATTACKING".equals(currentState) && engine.getAttackerIdx() == i) {
                    var hand = engine.getHand(i);
                    if (hand != null && !hand.isEmpty()) {
                        var card = hand.get(0);
                        boolean ok = engine.attack(i, card);
                        if (ok) actions.put("aiAction", "AI attacks with " + card.toDisplayString());
                        currentState = engine.getGameState();
                    }
                } else if ("DEFENDING".equals(currentState) && engine.getDefenderIdx() == i) {
                    var hand = engine.getHand(i);
                    if (hand != null && !hand.isEmpty()) {
                        boolean beatAny = false;
                        var handCopy = new java.util.ArrayList<>(hand);
                        for (var tc : engine.getTableCards()) {
                            if (!tc.isBeaten()) {
                                for (var card : handCopy) {
                                    if (card.beats(tc.getAttackCard(), engine.getTrump())) {
                                        engine.defend(engine.getTableCards().indexOf(tc), card);
                                        beatAny = true;
                                        actions.put("aiAction", "AI beats with " + card.toDisplayString());
                                        currentState = engine.getGameState();
                                        break;
                                    }
                                }
                                if (beatAny) break;
                            }
                        }
                        if (!beatAny) {
                            engine.takeCards();
                            actions.put("aiAction", "AI takes cards");
                            currentState = engine.getGameState();
                        }
                    }
                } else if ("THROWING_IN".equals(currentState) && engine.getDefenderIdx() != i) {
                    if (engine.hasPassed(i)) continue;
                    // Don't auto-process AI if a human non-defender hasn't passed yet
                    boolean hasHumanNonDefender = false;
                    for (int k = 0; k < players.size(); k++) {
                        if (!players.get(k).endsWith("-AI") && k != engine.getDefenderIdx() && !engine.hasPassed(k)) {
                            hasHumanNonDefender = true;
                            break;
                        }
                    }
                    if (hasHumanNonDefender) {
                        continue;
                    }
                    if (engine.canThrowIn(i) && Math.random() > 0.5) {
                        var hand = engine.getHand(i);
                        if (hand != null) {
                            boolean found = false;
                            var handCopy = new java.util.ArrayList<>(hand);
                            // Collect all valid throw-in ranks (attack + defense cards)
                            java.util.Set<Card.Rank> validRanks = new java.util.HashSet<>();
                            for (var tc : engine.getTableCards()) {
                                validRanks.add(tc.getAttackCard().getRank());
                                if (tc.getDefenseCard() != null) validRanks.add(tc.getDefenseCard().getRank());
                            }
                            for (var card : handCopy) {
                                if (found) break;
                                if (validRanks.contains(card.getRank())) {
                                    engine.throwCard(i, card);
                                    actions.put("aiAction", "AI throws " + card.toDisplayString());
                                    found = true;
                                    currentState = engine.getGameState();
                                }
                            }
                            if (!found) {
                                engine.pass(i);
                                actions.put("aiAction", "AI passes");
                                currentState = engine.getGameState();
                            }
                        }
                    } else {
                        engine.pass(i);
                        actions.put("aiAction", "AI passes");
                    }
                    currentState = engine.getGameState();
                }
            }
        }
        // Cascade AI turns: if AI changed game state, check for more AI actions
        String newState = engine.getGameState();
        boolean hasPendingAi = !newState.equals(state) || "ATTACKING".equals(newState) || "DEFENDING".equals(newState) || ("THROWING_IN".equals(newState) && engine.hasUnpassedThrowers());
        if (!actions.isEmpty() && hasPendingAi) {
            int depth = 0;
            while (depth < 100) {
                boolean acted = false;
                for (int j = 0; j < players.size(); j++) {
                    if (players.get(j).endsWith("-AI")) {
                        String s = engine.getGameState();
                        if ("ATTACKING".equals(s) && engine.getAttackerIdx() == j) {
                            var h = engine.getHand(j);
                            if (h != null && !h.isEmpty()) {
                                engine.attack(j, h.get(0));
                                actions.put("aiCascade", "AI attacks");
                                acted = true;
                            }
                        } else if ("DEFENDING".equals(s) && engine.getDefenderIdx() == j) {
                            var h = engine.getHand(j);
                            boolean beatAny = false;
                            if (h != null) {
                                var hCopy = new java.util.ArrayList<>(h);
                                for (var tc : engine.getTableCards()) {
                                    if (!tc.isBeaten()) {
                                        for (var card : hCopy) {
                                            if (card.beats(tc.getAttackCard(), engine.getTrump())) {
                                                engine.defend(engine.getTableCards().indexOf(tc), card);
                                                actions.put("aiCascade", "AI defends");
                                                acted = true;
                                                beatAny = true;
                                                break;
                                            }
                                        }
                                        if (beatAny) break;
                                    }
                                }
                                if (!beatAny) {
                                    engine.takeCards();
                                    actions.put("aiCascade", "AI takes cards");
                                    acted = true;
                                }
                            }
                        } else if ("THROWING_IN".equals(s) && engine.getDefenderIdx() != j) {
                            if (engine.hasPassed(j)) continue;
                            // Don't auto-process AI if a human non-defender hasn't passed yet
                            boolean hasHumanNonDefender = false;
                            for (int k = 0; k < players.size(); k++) {
                                if (!players.get(k).endsWith("-AI") && k != engine.getDefenderIdx() && !engine.hasPassed(k)) {
                                    hasHumanNonDefender = true;
                                    break;
                                }
                            }
                            if (hasHumanNonDefender) {
                                // Skip AI auto-throw - let human decide first
                                continue;
                            }
                            if (engine.canThrowIn(j) && Math.random() > 0.5) {
                                var h = engine.getHand(j);
                                if (h != null) {
                                    boolean found = false;
                                    var hCopy = new java.util.ArrayList<>(h);
                                    // Collect all valid throw-in ranks (attack + defense cards)
                                    java.util.Set<Card.Rank> validRanks = new java.util.HashSet<>();
                                    for (var tc : engine.getTableCards()) {
                                        validRanks.add(tc.getAttackCard().getRank());
                                        if (tc.getDefenseCard() != null) validRanks.add(tc.getDefenseCard().getRank());
                                    }
                                    for (var card : hCopy) {
                                        if (found) break;
                                        if (validRanks.contains(card.getRank())) {
                                            engine.throwCard(j, card);
                                            actions.put("aiCascade", "AI throws " + card.toDisplayString());
                                            found = true;
                                            acted = true;
                                        }
                                    }
                                    if (!found) {
                                        engine.pass(j);
                                        actions.put("aiCascade", "AI passes");
                                        acted = true;
                                    }
                                }
                            } else {
                                engine.pass(j);
                                actions.put("aiCascade", "AI passes");
                                acted = true;
                            }
                        }
                    }
                }
                if (!acted) break;
                depth++;
            }
        }
        return actions;
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}
