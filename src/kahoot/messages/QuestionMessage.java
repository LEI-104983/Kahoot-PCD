package kahoot.messages;

import kahoot.game.Question;

public class QuestionMessage extends Message {
    private final Question question;
    private final int timeLimit;
    private final int questionIndex;
    private final boolean isTeamQuestion;

    public QuestionMessage(String gameId, String teamId, String username,
                           Question question, int timeLimit, int questionIndex, boolean isTeamQuestion) {
        super(gameId, teamId, username);
        this.question = question;
        this.timeLimit = timeLimit;
        this.questionIndex = questionIndex;
        this.isTeamQuestion = isTeamQuestion;
    }

    public Question getQuestion() { return question; }
    public int getTimeLimit() { return timeLimit; }
    public int getQuestionIndex() { return questionIndex; }
    public boolean isTeamQuestion() { return isTeamQuestion; }
}