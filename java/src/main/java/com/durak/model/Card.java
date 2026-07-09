package com.durak.model;

public class Card {
    public enum Suit { SPADES, HEARTS, DIAMONDS, CLUBS }
    public enum Rank {
        TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8),
        NINE(9), TEN(10), JACK(11), QUEEN(12), KING(13), ACE(14);
        public final int value;
        Rank(int v) { this.value = v; }
    }

    private Suit suit;
    private Rank rank;
    private boolean isTrump;

    public Card(Suit suit, Rank rank) { this.suit = suit; this.rank = rank; }

    public boolean beats(Card other, Suit trump) {
        if (this.suit == trump && other.suit != trump) return true;
        if (this.suit != trump && other.suit == trump) return false;
        if (this.suit == other.suit) return this.rank.value > other.rank.value;
        return false;
    }

    public boolean sameRank(Card other) { return this.rank == other.rank; }

    public Suit getSuit() { return suit; }
    public Rank getRank() { return rank; }
    public boolean isTrump() { return isTrump; }
    public void setTrump(boolean t) { isTrump = t; }

    public String toDisplayString() {
        String[] symbols = {"♠", "♥", "♦", "♣"};
        return rank.name() + symbols[suit.ordinal()];
    }

    public String getSvgFile(boolean deck36) {
        String r = switch(rank) {
            case TWO -> "2"; case THREE -> "3"; case FOUR -> "4"; case FIVE -> "5";
            case SIX -> "6"; case SEVEN -> "7"; case EIGHT -> "8"; case NINE -> "9";
            case TEN -> "10"; case JACK -> "11-JACK"; case QUEEN -> "12-QUEEN";
            case KING -> "13-KING"; case ACE -> "1";
        };
        String s = switch(suit) {
            case SPADES -> "SPADE"; case HEARTS -> "HEART";
            case DIAMONDS -> "DIAMOND"; case CLUBS -> "CLUB";
        };
        return s + "-" + r + ".svg";
    }
}
