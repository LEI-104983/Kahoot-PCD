package kahoot.messages;

import java.io.Serializable;

public abstract class Message implements Serializable {
    private final String gameId;
    private final String teamId;
    private final String username;

    public Message(String gameId, String teamId, String username) {
        this.gameId = gameId;
        this.teamId = teamId;
        this.username = username;
    }

    public String getGameId() { return gameId; }
    public String getTeamId() { return teamId; }
    public String getUsername() { return username; }
}