package com.durak.service;

import com.durak.game.DurakEngine;
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
                    if (engine.canThrowIn(i) && Math.random() > 0.5) {
                        var hand = engine.getHand(i);
                        if (hand != null) {
                            boolean found = false;
                            var handCopy = new java.util.ArrayList<>(hand);
                            for (var card : handCopy) {
                                if (found) break;
                                for (var tc : engine.getTableCards()) {
                                    if (card.getRank() == tc.getAttackCard().getRank()) {
                                        engine.throwCard(i, card);
                                        actions.put("aiAction", "AI throws " + card.toDisplayString());
                                        found = true;
                                        currentState = engine.getGameState();
                                        break;
                                    }
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
        boolean hasPendingAi = !newState.equals(state) || "ATTACKING".equals(newState) || ("THROWING_IN".equals(newState) && engine.hasUnpassedThrowers());
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
                            if (engine.canThrowIn(j) && Math.random() > 0.5) {
                                var h = engine.getHand(j);
                                if (h != null) {
                                    boolean found = false;
                                    var hCopy = new java.util.ArrayList<>(h);
                                    for (var card : hCopy) {
                                        if (found) break;
                                        for (var tc : engine.getTableCards()) {
                                            if (card.getRank() == tc.getAttackCard().getRank()) {
                                                engine.throwCard(j, card);
                                                actions.put("aiCascade", "AI throws " + card.toDisplayString());
                                                found = true;
                                                acted = true;
                                                break;
                                            }
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
