package com.durak.controller;

import com.durak.game.DurakEngine;
import com.durak.model.Card;
import com.durak.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.*;

@Controller
public class GameWebSocketController {

    @Autowired
    private GameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private Card findCard(DurakEngine engine, int playerIdx, String rank, String suit) {
        if (engine.getHand(playerIdx) == null) return null;
        for (var card : engine.getHand(playerIdx)) {
            if (card.getRank().name().equals(rank) && card.getSuit().name().equals(suit)) {
                return card;
            }
        }
        return null;
    }

    private void broadcastState(String roomCode) {
        Map<String, Object> state = gameService.buildStateMap(roomCode);
        messagingTemplate.convertAndSend("/topic/game/" + roomCode, state);
    }

    @MessageMapping("/game/{roomCode}/attack")
    public void handleAttack(@DestinationVariable String roomCode, @Payload Map<String, Object> payload) {
        int playerIdx = ((Number) payload.get("playerIdx")).intValue();
        String rank = (String) payload.get("rank");
        String suit = (String) payload.get("suit");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return;
        var card = findCard(engine, playerIdx, rank, suit);
        if (card == null) return;
        engine.attack(playerIdx, card);
        gameService.processAiTurn(roomCode);
        broadcastState(roomCode);
    }

    @MessageMapping("/game/{roomCode}/defend")
    public void handleDefend(@DestinationVariable String roomCode, @Payload Map<String, Object> payload) {
        int playerIdx = ((Number) payload.get("playerIdx")).intValue();
        int attackIdx = ((Number) payload.get("attackIdx")).intValue();
        String rank = (String) payload.get("rank");
        String suit = (String) payload.get("suit");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return;
        var card = findCard(engine, playerIdx, rank, suit);
        if (card == null) return;
        engine.defend(attackIdx, card);
        gameService.processAiTurn(roomCode);
        broadcastState(roomCode);
    }

    @MessageMapping("/game/{roomCode}/throw")
    public void handleThrow(@DestinationVariable String roomCode, @Payload Map<String, Object> payload) {
        int playerIdx = ((Number) payload.get("playerIdx")).intValue();
        String rank = (String) payload.get("rank");
        String suit = (String) payload.get("suit");
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return;
        var card = findCard(engine, playerIdx, rank, suit);
        if (card == null) return;
        engine.throwCard(playerIdx, card);
        gameService.processAiTurn(roomCode);
        broadcastState(roomCode);
    }

    @MessageMapping("/game/{roomCode}/pass")
    public void handlePass(@DestinationVariable String roomCode, @Payload Map<String, Object> payload) {
        int playerIdx = ((Number) payload.get("playerIdx")).intValue();
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return;
        engine.pass(playerIdx);
        gameService.processAiTurn(roomCode);
        broadcastState(roomCode);
    }

    @MessageMapping("/game/{roomCode}/take")
    public void handleTake(@DestinationVariable String roomCode, @Payload Map<String, Object> payload) {
        DurakEngine engine = gameService.getEngine(roomCode);
        if (engine == null) return;
        engine.takeCards();
        gameService.processAiTurn(roomCode);
        broadcastState(roomCode);
    }
}
