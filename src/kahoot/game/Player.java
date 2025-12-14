package kahoot.game;

import java.io.Serializable;

public class Player implements Serializable {
    private final String username;
    private final String teamId;
    private int score;

    public Player(String username, String teamId) {
        this.username = username;
        this.teamId = teamId;
        this.score = 0;
    }

    public String getUsername() { return username; }
    public String getTeamId() { return teamId; }
    public int getScore() { return score; }
    
    // Thread-safe: usando synchronized para evitar race conditions
    public synchronized void addScore(int points) { 
        this.score += points; 
    }
}