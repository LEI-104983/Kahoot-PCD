package kahoot.messages;

import java.util.Map;

public class ScoreMessage extends Message {
    private final Map<String, Integer> teamScores;
    private final int currentRoundPoints;
    private final int questionIndex;

    public ScoreMessage(String gameId, String teamId, String username,
                        Map<String, Integer> teamScores, int currentRoundPoints, int questionIndex) {
        super(gameId, teamId, username);
        this.teamScores = teamScores;
        this.currentRoundPoints = currentRoundPoints;
        this.questionIndex = questionIndex;
    }

    public Map<String, Integer> getTeamScores() { return teamScores; }
    public int getCurrentRoundPoints() { return currentRoundPoints; }
    public int getQuestionIndex() { return questionIndex; }
}