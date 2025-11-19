package kahoot.messages;

import java.util.Map;

public class GameEndMessage extends Message {
    private final Map<String, Integer> finalScores;
    private final String winningTeam;

    public GameEndMessage(String gameId, String teamId, String username,
                          Map<String, Integer> finalScores, String winningTeam) {
        super(gameId, teamId, username);
        this.finalScores = finalScores;
        this.winningTeam = winningTeam;
    }

    public Map<String, Integer> getFinalScores() { return finalScores; }
    public String getWinningTeam() { return winningTeam; }
}