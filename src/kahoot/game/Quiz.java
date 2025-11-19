package kahoot.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Quiz implements Serializable {
    private final String name;
    private final List<Question> questions;
    private int currentQuestion;

    public Quiz(String name) {
        this.name = name;
        this.questions = new ArrayList<>();
        this.currentQuestion = 0;
    }

    public void addQuestion(Question question) {
        questions.add(question);
    }

    public Question getNextQuestion() {
        if (currentQuestion < questions.size()) {
            return questions.get(currentQuestion++);
        }
        return null;
    }

    public boolean hasMoreQuestions() {
        return currentQuestion < questions.size();
    }

    public int getTotalQuestions() {
        return questions.size();
    }

    public int getCurrentQuestionIndex() {
        return currentQuestion;
    }

    public void reset() {
        currentQuestion = 0;
    }
}