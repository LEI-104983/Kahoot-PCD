package kahoot.game;

import java.io.Serializable;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

public class Quiz implements Serializable {
    private final String name;
    private final List<Question> questions;
    private int currentQuestion;
    private final Random random;

    public Quiz(String name, List<Question> questions) {
        this.name = name;
        this.questions = questions;
        this.currentQuestion = 0;
        this.random = new Random();
    }

    public static Quiz loadFromJson(String filename, int numQuestions) throws IOException {
        try (FileReader reader = new FileReader(filename)) {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            // Extrair o primeiro quiz do array "quizzes"
            JsonArray quizzesArray = jsonObject.getAsJsonArray("quizzes");
            if (quizzesArray == null || quizzesArray.size() == 0) {
                throw new IOException("Ficheiro JSON não contém quizzes");
            }

            JsonObject firstQuiz = quizzesArray.get(0).getAsJsonObject();
            String quizName = firstQuiz.get("name").getAsString();

            // Carregar todas as perguntas
            JsonArray questionsArray = firstQuiz.getAsJsonArray("questions");
            List<Question> allQuestions = new ArrayList<>();

            for (JsonElement questionElement : questionsArray) {
                JsonObject questionObj = questionElement.getAsJsonObject();

                String questionText = questionObj.get("question").getAsString();
                int points = questionObj.get("points").getAsInt();
                int correct = questionObj.get("correct").getAsInt();

                // Carregar opções
                JsonArray optionsArray = questionObj.getAsJsonArray("options");
                String[] options = new String[optionsArray.size()];
                for (int i = 0; i < optionsArray.size(); i++) {
                    options[i] = optionsArray.get(i).getAsString();
                }

                Question question = new Question(questionText, options, correct, points);
                allQuestions.add(question);
            }

            // 🔄 SELEÇÃO ALEATÓRIA de perguntas
            List<Question> selectedQuestions = selectRandomQuestions(allQuestions, numQuestions);

            System.out.println("✅ Quiz carregado: " + quizName +
                    " | " + selectedQuestions.size() + "/" + allQuestions.size() +
                    " perguntas selecionadas aleatoriamente");

            return new Quiz(quizName, selectedQuestions);

        } catch (Exception e) {
            throw new IOException("Erro ao carregar quiz do ficheiro: " + e.getMessage(), e);
        }
    }

    /**
     * Seleciona N perguntas aleatórias da lista completa
     */
    private static List<Question> selectRandomQuestions(List<Question> allQuestions, int numQuestions) {
        if (numQuestions >= allQuestions.size()) {
            // Se pedirmos mais perguntas do que existem, devolver todas
            return new ArrayList<>(allQuestions);
        }

        // Embaralhar a lista e pegar as primeiras N
        List<Question> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);

        return shuffled.subList(0, numQuestions);
    }

    /**
     * Alternativa: seleciona perguntas aleatórias mas garante alternância entre individuais/equipa
     */
    public static Quiz loadFromJsonBalanced(String filename, int numQuestions) throws IOException {
        try (FileReader reader = new FileReader(filename)) {
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);

            JsonArray quizzesArray = jsonObject.getAsJsonArray("quizzes");
            if (quizzesArray == null || quizzesArray.size() == 0) {
                throw new IOException("Ficheiro JSON não contém quizzes");
            }

            JsonObject firstQuiz = quizzesArray.get(0).getAsJsonObject();
            String quizName = firstQuiz.get("name").getAsString();

            // Carregar todas as perguntas
            JsonArray questionsArray = firstQuiz.getAsJsonArray("questions");
            List<Question> allQuestions = new ArrayList<>();

            for (JsonElement questionElement : questionsArray) {
                JsonObject questionObj = questionElement.getAsJsonObject();

                String questionText = questionObj.get("question").getAsString();
                int points = questionObj.get("points").getAsInt();
                int correct = questionObj.get("correct").getAsInt();

                JsonArray optionsArray = questionObj.getAsJsonArray("options");
                String[] options = new String[optionsArray.size()];
                for (int i = 0; i < optionsArray.size(); i++) {
                    options[i] = optionsArray.get(i).getAsString();
                }

                Question question = new Question(questionText, options, correct, points);
                allQuestions.add(question);
            }

            // 🔄 SELEÇÃO ALEATÓRIA BALANCEADA
            List<Question> selectedQuestions = selectBalancedRandomQuestions(allQuestions, numQuestions);

            System.out.println("✅ Quiz balanceado carregado: " + quizName +
                    " | " + selectedQuestions.size() + " perguntas selecionadas");

            return new Quiz(quizName, selectedQuestions);

        } catch (Exception e) {
            throw new IOException("Erro ao carregar quiz do ficheiro: " + e.getMessage(), e);
        }
    }

    /**
     * Seleção balanceada - garante que temos mistura de tipos de perguntas
     * (útil se quiser garantir alternância entre individuais/equipa)
     */
    private static List<Question> selectBalancedRandomQuestions(List<Question> allQuestions, int numQuestions) {
        List<Question> shuffled = new ArrayList<>(allQuestions);
        Collections.shuffle(shuffled);

        if (numQuestions >= allQuestions.size()) {
            return shuffled;
        }

        return shuffled.subList(0, numQuestions);
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

    public String getName() {
        return name;
    }

    /**
     * Método para debug - mostra as perguntas selecionadas
     */
    public void printSelectedQuestions() {
        System.out.println("📋 Perguntas selecionadas aleatoriamente:");
        for (int i = 0; i < questions.size(); i++) {
            System.out.println((i + 1) + ". " + questions.get(i).getQuestion());
        }
    }
}