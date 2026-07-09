package com.durak.game;

import com.durak.model.Card;
import com.durak.model.Card.Suit;
import com.durak.model.Card.Rank;
import com.durak.model.TableCard;

import java.util.*;
import java.util.stream.Collectors;

public class DurakEngine {
    private List<Card> deck;
    private List<List<Card>> playerHands;
    private List<TableCard> tableCards;
    private Suit trump;
    private int attackerIdx, defenderIdx;
    private int deckType;
    private String mode; // "throwin" or "transfer"
    private List<Integer> activePlayers;
    private boolean[] leftGame;
    private int turnNumber;
    private String lastAction;
    private final List<String> actionLog = new ArrayList<>();
    private String gameState; // ATTACKING, DEFENDING, THROWING_IN, TRANSFERRING, FINISHED
    private List<Integer> throwInOrder;
    private Set<Integer> passedPlayers;

    public DurakEngine(int deckType, String mode, int playerCount) {
        this.deckType = deckType;
        this.mode = mode;
        this.deck = createDeck(deckType);
        Collections.shuffle(this.deck, new Random());
        this.trump = deck.get(deck.size()-1).getSuit();
        deck.get(deck.size()-1).setTrump(true);

        this.playerHands = new ArrayList<>();
        this.activePlayers = new ArrayList<>();
        this.leftGame = new boolean[playerCount];
        for (int i = 0; i < playerCount; i++) {
            playerHands.add(new ArrayList<>());
            activePlayers.add(i);
        }

        for (int i = 0; i < 6; i++)
            for (var hand : playerHands)
                if (!deck.isEmpty()) hand.add(deck.remove(deck.size()-1));

        this.tableCards = new ArrayList<>();
        this.turnNumber = 1;
        this.gameState = "ATTACKING";
        this.passedPlayers = new HashSet<>();

        // Find first attacker (lowest trump)
        int minPlayer = 0;
        Rank minRank = Rank.ACE;
        for (int i = 0; i < playerCount; i++) {
            for (Card c : playerHands.get(i)) {
                if (c.getSuit() == trump && c.getRank().value < minRank.value) {
                    minRank = c.getRank();
                    minPlayer = i;
                }
            }
        }
        this.attackerIdx = minPlayer;
        this.defenderIdx = nextPlayer(attackerIdx);
        this.lastAction = "Game started! Trump: " + trump;
        addLog(lastAction);
    }

    private List<Card> createDeck(int type) {
        List<Card> d = new ArrayList<>();
        Rank[] ranks = type == 36 ?
            new Rank[]{Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE} :
            Rank.values();
        for (Suit s : Suit.values())
            for (Rank r : ranks)
                d.add(new Card(s, r));
        return d;
    }

    public int nextPlayer(int from) {
        for (int i = 1; i <= activePlayers.size(); i++) {
            int next = (from + i) % activePlayers.size();
            if (!leftGame[activePlayers.get(next)]) return activePlayers.get(next);
        }
        return from;
    }

    public boolean attack(int playerIdx, Card card) {
        if (!gameState.equals("ATTACKING") || playerIdx != attackerIdx) return false;
        List<Card> hand = playerHands.get(playerIdx);
        if (!hand.contains(card)) return false;
        hand.remove(card);
        tableCards.add(new TableCard(card, playerIdx));
        lastAction = "Player " + playerIdx + " attacks with " + card.toDisplayString();
        addLog(lastAction);

        if (mode.equals("throwin")) {
            throwInOrder = new ArrayList<>(activePlayers);
            throwInOrder.remove((Integer) defenderIdx);
            if (activePlayers.size() <= 2) {
                throwInOrder.remove((Integer) attackerIdx);
            }
            passedPlayers = new HashSet<>();
            if (throwInOrder.isEmpty()) {
                gameState = "DEFENDING";
            } else {
                gameState = "THROWING_IN";
            }
        } else {
            gameState = "DEFENDING";
        }
        return true;
    }

    public boolean defend(int attackIdx, Card defCard) {
        if (!gameState.equals("DEFENDING")) return false;
        TableCard tc = tableCards.get(attackIdx);
        if (tc.isBeaten()) return false;
        List<Card> hand = playerHands.get(defenderIdx);
        if (!hand.contains(defCard)) return false;
        if (!defCard.beats(tc.getAttackCard(), trump)) return false;

        hand.remove(defCard);
        tc.setDefenseCard(defCard);
        lastAction = "Defender beats with " + defCard.toDisplayString();
        addLog(lastAction);

        if (tableCards.stream().allMatch(TableCard::isBeaten)) {
            if (mode.equals("throwin") && !throwInOrder.isEmpty()) {
                gameState = "THROWING_IN";
                passedPlayers = new HashSet<>();
                lastAction = "All cards beaten! Throw more or pass";
                addLog(lastAction);
            } else {
                finishDefense();
            }
        }
        return true;
    }

    public boolean takeCards() {
        if (!gameState.equals("DEFENDING") && !gameState.equals("THROWING_IN")) return false;
        List<Card> hand = playerHands.get(defenderIdx);
        for (TableCard tc : tableCards) {
            hand.add(tc.getAttackCard());
            if (tc.getDefenseCard() != null) hand.add(tc.getDefenseCard());
        }
        tableCards.clear();
        lastAction = "Defender takes cards!";
        addLog(lastAction);
        attackerIdx = nextPlayer(attackerIdx);
        defenderIdx = nextPlayer(attackerIdx);
        finishTurn();
        return true;
    }

    private void finishDefense() {
        tableCards.clear();
        lastAction = "All cards beaten!";
        addLog(lastAction);
        attackerIdx = nextPlayer(attackerIdx);
        defenderIdx = nextPlayer(attackerIdx);
        finishTurn();
    }

    private void finishTurn() {
        drawCards();
        checkGameOver();
        if (!gameState.equals("FINISHED")) {
            gameState = "ATTACKING";
            turnNumber++;
        }
    }

    private void drawCards() {
        for (int i : activePlayers) {
            if (leftGame[i]) continue;
            while (playerHands.get(i).size() < 6 && !deck.isEmpty())
                playerHands.get(i).add(deck.remove(deck.size()-1));
        }
    }

    private void checkGameOver() {
        if (!deck.isEmpty()) return;
        List<Integer> withCards = new ArrayList<>();
        for (int i : activePlayers) {
            if (!leftGame[i] && !playerHands.get(i).isEmpty()) withCards.add(i);
            else if (!leftGame[i]) leftGame[i] = true;
        }
        if (withCards.size() <= 1) {
            gameState = "FINISHED";
            if (withCards.size() == 1) {
                lastAction = "Player " + withCards.get(0) + " is the FOOL!";
                addLog(lastAction);
            } else {
                lastAction = "Game Over - everyone left!";
                addLog(lastAction);
            }
        }
    }

    public boolean canThrowIn(int playerIdx) {
        if (!gameState.equals("THROWING_IN")) return false;
        Set<Rank> ranks = tableCards.stream().map(tc -> tc.getAttackCard().getRank()).collect(Collectors.toSet());
        return playerHands.get(playerIdx).stream().anyMatch(c -> ranks.contains(c.getRank()));
    }

    public boolean throwCard(int playerIdx, Card card) {
        if (!gameState.equals("THROWING_IN")) return false;
        if (!throwInOrder.contains(playerIdx)) return false;
        if (passedPlayers.contains(playerIdx)) return false;
        List<Card> hand = playerHands.get(playerIdx);
        if (!hand.contains(card)) return false;
        Set<Rank> tableRanks = tableCards.stream()
            .map(tc -> tc.getAttackCard().getRank())
            .collect(Collectors.toSet());
        if (!tableRanks.contains(card.getRank())) return false;
        hand.remove(card);
        tableCards.add(new TableCard(card, playerIdx));
        lastAction = "Player " + playerIdx + " throws " + card.toDisplayString();
        addLog(lastAction);
        return true;
    }

    public boolean pass(int playerIdx) {
        if (!gameState.equals("THROWING_IN")) return false;
        if (!throwInOrder.contains(playerIdx)) return false;
        if (passedPlayers.contains(playerIdx)) return false;
        passedPlayers.add(playerIdx);
        lastAction = "Player " + playerIdx + " passes";
        addLog(lastAction);
        if (passedPlayers.size() >= throwInOrder.size()) {
            if (mode.equals("throwin") && !tableCards.stream().allMatch(TableCard::isBeaten)) {
                gameState = "DEFENDING";
                lastAction = "All passed! Defender must beat or take";
                addLog(lastAction);
            } else {
                finishDefense();
            }
        }
        return true;
    }

    private void addLog(String msg) {
        actionLog.add("[Turn " + turnNumber + "] " + msg);
        System.out.println("[DURAK] [Turn " + turnNumber + "] " + msg);
    }

    public boolean hasPassed(int playerIdx) { return passedPlayers.contains(playerIdx); }
    public boolean hasUnpassedThrowers() {
        if (!gameState.equals("THROWING_IN")) return false;
        for (int p : throwInOrder) {
            if (!passedPlayers.contains(p)) return true;
        }
        return false;
    }

    public List<String> getActionLog() { return actionLog; }

    // Getters
    public String getGameState() { return gameState; }
    public int getAttackerIdx() { return attackerIdx; }
    public int getDefenderIdx() { return defenderIdx; }
    public Suit getTrump() { return trump; }
    public int getTurnNumber() { return turnNumber; }
    public int getDeckSize() { return deck.size(); }
    public List<Card> getHand(int player) { return playerHands.get(player); }
    public List<TableCard> getTableCards() { return tableCards; }
    public String getLastAction() { return lastAction; }
    public boolean isLeftGame(int player) { return leftGame[player]; }
}
