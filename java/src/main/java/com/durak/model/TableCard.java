package com.durak.model;

public class TableCard {
    private Card attackCard;
    private Card defenseCard;
    private boolean beaten;
    private int attackerIndex;

    public TableCard(Card attackCard, int attackerIndex) {
        this.attackCard = attackCard;
        this.attackerIndex = attackerIndex;
        this.beaten = false;
    }

    public Card getAttackCard() { return attackCard; }
    public Card getDefenseCard() { return defenseCard; }
    public void setDefenseCard(Card c) { this.defenseCard = c; this.beaten = true; }
    public boolean isBeaten() { return beaten; }
    public int getAttackerIndex() { return attackerIndex; }
}
