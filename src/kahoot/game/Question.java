package kahoot.game;

import java.io.Serializable;

public class Question implements Serializable {
    private final String question;
    private final String[] options;
    private final int correct;
    private final int points;

    public Question(String question, String[] options, int correct, int points) {
        this.question = question;
        this.options = options;
        this.correct = correct;
        this.points = points;
    }

    public String getQuestion() { return question; }
    public String[] getOptions() { return options; }
    public int getCorrect() { return correct; }
    public int getPoints() { return points; }
    public boolean isCorrect(int answer) { return answer == correct; }
}