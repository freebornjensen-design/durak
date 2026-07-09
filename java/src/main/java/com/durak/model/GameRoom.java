package com.durak.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "game_rooms")
public class GameRoom {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String roomCode;
    private int deckType; // 36 or 52
    private String gameMode; // "throwin" or "transfer"
    private int maxPlayers;
    private boolean started;
    private boolean finished;
    private String trumpSuit;
    @Column(columnDefinition = "TEXT")
    private String gameStateJson;
    private int currentAttacker;
    private int currentDefender;
    @ElementCollection
    private List<String> playerNames = new ArrayList<>();
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt = new Date();
    private int turnNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public int getDeckType() { return deckType; }
    public void setDeckType(int deckType) { this.deckType = deckType; }
    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public boolean isStarted() { return started; }
    public void setStarted(boolean started) { this.started = started; }
    public boolean isFinished() { return finished; }
    public void setFinished(boolean finished) { this.finished = finished; }
    public String getTrumpSuit() { return trumpSuit; }
    public void setTrumpSuit(String trumpSuit) { this.trumpSuit = trumpSuit; }
    public String getGameStateJson() { return gameStateJson; }
    public void setGameStateJson(String gameStateJson) { this.gameStateJson = gameStateJson; }
    public int getCurrentAttacker() { return currentAttacker; }
    public void setCurrentAttacker(int currentAttacker) { this.currentAttacker = currentAttacker; }
    public int getCurrentDefender() { return currentDefender; }
    public void setCurrentDefender(int currentDefender) { this.currentDefender = currentDefender; }
    public List<String> getPlayerNames() { return playerNames; }
    public void setPlayerNames(List<String> playerNames) { this.playerNames = playerNames; }
    public Date getCreatedAt() { return createdAt; }
    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }
}
