package kahoot.messages;

public class ErrorMessage extends Message {
    private final String errorMessage;

    public ErrorMessage(String gameId, String teamId, String username, String errorMessage) {
        super(gameId, teamId, username);
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() { return errorMessage; }
}