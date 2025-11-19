package kahoot.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Team implements Serializable {
    private final String teamId;
    private final List<Player> players;
    private int teamScore;

    public Team(String teamId) {
        this.teamId = teamId;
        this.players = new ArrayList<>();
        this.teamScore = 0;
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public List<Player> getPlayers() { return players; }
    public String getTeamId() { return teamId; }
    public int getTeamScore() { return teamScore; }
    public void addTeamScore(int points) { this.teamScore += points; }
    public int getPlayerCount() { return players.size(); }
}