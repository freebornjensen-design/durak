package com.durak.controller;

import com.durak.game.DurakEngine;
import com.durak.model.Card;
import com.durak.model.GameRoom;
import com.durak.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;

@RestController
@RequestMapping("/api/game")
public class GameController {
    @Autowired
    private GameService gameService;

    private static final String WEBHOOK_SECRET = System.getenv("WEBHOOK_SECRET");

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody Map<String, Object> body) {
        int deckType = (int) body.getOrDefault("deckType", 36);
        String mode = (String) body.getOrDefault("mode", "throwin");
        int maxPlayers = (int) body.getOrDefault("maxPlayers", 4);
        var room = gameService.createRoom(deckType, mode, maxPlayers);
        Map<String, Object> res = new HashMap<>();
        res.put("roomCode", room.getRoomCode());
        res.put("id", room.getId());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinRoom(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        String playerName = (String) body.get("playerName");
        boolean joined = gameService.joinRoom(roomCode, playerName);
        Map<String, Object> res = new HashMap<>();
        res.put("success", joined);
        if (!joined) res.put("error", "Cannot join room");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startGame(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        GameRoom room = gameService.startGame(roomCode);
        Map<String, Object> res = new HashMap<>();
        if (room == null) {
            res.put("error", "Cannot start game");
            return ResponseEntity.badRequest().body(res);
        }
        res.put("started", true);
        res.put("trump", room.getTrumpSuit());
        res.put("players", gameService.getPlayers(roomCode));

        // If first attacker is AI, trigger AI turn immediately
        Map<String, Object> aiActions = gameService.processAiTurn(roomCode);
        if (aiActions != null && !aiActions.isEmpty()) {
            res.put("aiActions", aiActions);
        }

        return ResponseEntity.ok(res);
    }

    @GetMapping("/state/{roomCode}")
    public ResponseEntity<Map<String, Object>> getState(@PathVariable String roomCode) {
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) {
            // Return players list even if game not started
            List<String> players = gameService.getPlayers(roomCode);
            Map<String, Object> waiting = new HashMap<>();
            waiting.put("gameState", "WAITING");
            waiting.put("players", players != null ? players : new ArrayList<>());
            return ResponseEntity.ok(waiting);
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
        state.put("players", gameService.getPlayers(roomCode));

        List<List<Map<String, Object>>> hands = new ArrayList<>();
        for (int i = 0; i < gameService.getPlayers(roomCode).size(); i++) {
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
        state.put("hands", hands);

        if ("FINISHED".equals(engine.getGameState())) {
            state.put("foolIdx", engine.getDefenderIdx());
        }
        return ResponseEntity.ok(state);
    }

    @PostMapping("/attack")
    public ResponseEntity<Map<String, Object>> attack(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        int playerIdx = (int) body.get("playerIdx");
        String rankStr = (String) body.get("rank");
        String suitStr = (String) body.get("suit");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return ResponseEntity.badRequest().build();
        var card = findCard(engine, playerIdx, rankStr, suitStr);
        if (card == null) return ResponseEntity.badRequest().build();
        engine.attack(playerIdx, card);
        gameService.processAiTurn(roomCode);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/defend")
    public ResponseEntity<Map<String, Object>> defend(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        int playerIdx = (int) body.get("playerIdx");
        int attackIdx = (int) body.get("attackIdx");
        String rankStr = (String) body.get("rank");
        String suitStr = (String) body.get("suit");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return ResponseEntity.badRequest().build();
        var card = findCard(engine, playerIdx, rankStr, suitStr);
        if (card == null) return ResponseEntity.badRequest().build();
        engine.defend(attackIdx, card);
        gameService.processAiTurn(roomCode);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/take")
    public ResponseEntity<Map<String, Object>> take(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return ResponseEntity.badRequest().build();
        engine.takeCards();
        gameService.processAiTurn(roomCode);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/throw")
    public ResponseEntity<Map<String, Object>> throwCard(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        int playerIdx = (int) body.get("playerIdx");
        String rankStr = (String) body.get("rank");
        String suitStr = (String) body.get("suit");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return ResponseEntity.badRequest().build();
        var card = findCard(engine, playerIdx, rankStr, suitStr);
        if (card == null) return ResponseEntity.badRequest().build();
        engine.throwCard(playerIdx, card);
        gameService.processAiTurn(roomCode);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/pass")
    public ResponseEntity<Map<String, Object>> pass(@RequestBody Map<String, Object> body) {
        String roomCode = (String) body.get("roomCode");
        int playerIdx = (int) body.get("playerIdx");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return ResponseEntity.badRequest().build();
        engine.pass(playerIdx);
        gameService.processAiTurn(roomCode);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/debug/{roomCode}")
    public ResponseEntity<Map<String, Object>> debug(@PathVariable String roomCode) {
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return ResponseEntity.badRequest().build();
        Map<String, Object> dbg = new HashMap<>();
        dbg.put("gameState", engine.getGameState());
        dbg.put("turnNumber", engine.getTurnNumber());
        dbg.put("attackerIdx", engine.getAttackerIdx());
        dbg.put("defenderIdx", engine.getDefenderIdx());
        dbg.put("trumpSuit", engine.getTrump().name());
        dbg.put("deckSize", engine.getDeckSize());
        dbg.put("actionLog", engine.getActionLog());
        dbg.put("lastAction", engine.getLastAction());
        dbg.put("tableCards", engine.getTableCards());
        dbg.put("players", gameService.getPlayers(roomCode));
        dbg.put("throwInOrder", null);
        return ResponseEntity.ok(dbg);
    }

    @PostMapping("/deploy-webhook")
    public ResponseEntity<Map<String, Object>> deployWebhook(@RequestBody String body,
                                                              @RequestHeader(value="X-Hub-Signature-256", required=false) String signature,
                                                              @RequestHeader(value="X-GitHub-Event", required=false) String event) {
        Map<String, Object> res = new HashMap<>();
        try {
            // Validate HMAC signature
            if (WEBHOOK_SECRET == null) {
                res.put("success", false);
                res.put("error", "Server misconfigured: WEBHOOK_SECRET not set");
                return ResponseEntity.status(500).body(res);
            }
            if (signature == null || signature.isEmpty()) {
                res.put("success", false);
                res.put("error", "Missing signature");
                return ResponseEntity.status(401).body(res);
            }
            // Only run deploy on push events
            if (!"push".equals(event)) {
                res.put("success", true);
                res.put("message", "Ignored non-push event: " + event);
                System.out.println("[WEBHOOK] Ignored non-push event: " + event);
                return ResponseEntity.ok(res);
            }
            String expectedSig = "sha256=" + hmacSha256(body, WEBHOOK_SECRET);
            if (!expectedSig.equals(signature)) {
                res.put("success", false);
                res.put("error", "Invalid signature");
                return ResponseEntity.status(401).body(res);
            }

            ProcessBuilder pb = new ProcessBuilder("/var/www/durak/deploy.sh");
            pb.directory(new File("/var/www/durak"));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Read output in background to avoid blocking
            Thread thread = new Thread(() -> {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[WEBHOOK] " + line);
                    }
                    p.waitFor();
                    System.out.println("[WEBHOOK] Deploy exit code: " + p.exitValue());
                } catch (Exception e) {
                    System.err.println("[WEBHOOK] Error: " + e.getMessage());
                }
            });
            thread.setDaemon(true);
            // Log webhook call
            System.out.println("[WEBHOOK] Deploy triggered, event=" + event);
            thread.start();
            res.put("success", true);
            res.put("message", "Deploy started");
        } catch (Exception e) {
            res.put("success", false);
            res.put("error", e.getMessage());
        }
        return ResponseEntity.ok(res);
    }


    private Card findCard(DurakEngine engine, int playerIdx, String rank, String suit) {
        if (engine.getHand(playerIdx) == null) return null;
        for (var card : engine.getHand(playerIdx)) {
            if (card.getRank().name().equals(rank) && card.getSuit().name().equals(suit)) {
                return card;
            }
        }
        return null;
    }
    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : rawHmac) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
