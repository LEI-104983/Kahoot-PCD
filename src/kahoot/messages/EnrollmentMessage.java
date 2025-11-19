package kahoot.messages;

public class EnrollmentMessage extends Message {
    public EnrollmentMessage(String gameId, String teamId, String username) {
        super(gameId, teamId, username);
    }
}