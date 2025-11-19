package kahoot.messages;

public class AnswerMessage extends Message {
    private final int questionIndex;
    private final int answer;
    private final long timestamp;

    public AnswerMessage(String gameId, String teamId, String username,
                         int questionIndex, int answer) {
        super(gameId, teamId, username);
        this.questionIndex = questionIndex;
        this.answer = answer;
        this.timestamp = System.currentTimeMillis();
    }

    public int getQuestionIndex() { return questionIndex; }
    public int getAnswer() { return answer; }
    public long getTimestamp() { return timestamp; }
}