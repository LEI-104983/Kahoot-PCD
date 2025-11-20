package kahoot.game;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

public class Game implements Serializable {
    private final String gameId;
    private final int numTeams;
    private final int playersPerTeam;
    private final int numQuestions;
    private final Quiz quiz;
    private final Map<String, Team> teams;
    private int currentQuestionIndex;
    private boolean gameStarted;
    private boolean gameEnded;

    public Game(String gameId, int numTeams, int playersPerTeam, int numQuestions) {
        this.gameId = gameId;
        this.numTeams = numTeams;
        this.playersPerTeam = playersPerTeam;
        this.numQuestions = numQuestions;
        this.teams = new HashMap<>();
        this.quiz = createQuiz();
        this.currentQuestionIndex = 0;
        this.gameStarted = false;
        this.gameEnded = false;

        // Criar equipas
        for (int i = 1; i <= numTeams; i++) {
            teams.put("Team" + i, new Team("Team" + i));
        }
    }

    private Quiz createQuiz() {
        try {
            // FASE 3: Carregar perguntas aleatórias do JSON
            Quiz quiz = Quiz.loadFromJson("quizzes.json", this.numQuestions);

            // Opcional: mostrar perguntas selecionadas (debug)
            quiz.printSelectedQuestions();

            return quiz;

        } catch (IOException e) {
            System.err.println(" Não foi possível carregar o quiz do ficheiro JSON");
            System.err.println("  Detalhes: " + e.getMessage());
            System.err.println("  Certifique-se que o ficheiro de quizzes está na pasta corretaa");

            // Encerrar o jogo se não conseguir carregar as perguntas
            throw new RuntimeException("Não foi possível carregar perguntas do ficheiro JSON", e);
        }
    }

    public boolean addPlayer(String teamId, String username) {
        Team team = teams.get(teamId);
        if (team == null || team.getPlayerCount() >= playersPerTeam) {
            return false;
        }

        // Verificar se username já existe
        for (Team t : teams.values()) {
            for (Player p : t.getPlayers()) {
                if (p.getUsername().equals(username)) {
                    return false;
                }
            }
        }

        team.addPlayer(new Player(username, teamId));
        return true;
    }

    public boolean canStartGame() {
        for (Team team : teams.values()) {
            if (team.getPlayerCount() < playersPerTeam) {
                return false;
            }
        }
        return true;
    }

    // Getters
    public String getGameId() { return gameId; }
    public Map<String, Team> getTeams() { return teams; }
    public Quiz getQuiz() { return quiz; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public boolean isGameStarted() { return gameStarted; }
    public boolean isGameEnded() { return gameEnded; }
    public void setGameStarted(boolean started) { this.gameStarted = started; }
    public void setGameEnded(boolean ended) { this.gameEnded = ended; }
    public void incrementQuestion() { this.currentQuestionIndex++; }
}