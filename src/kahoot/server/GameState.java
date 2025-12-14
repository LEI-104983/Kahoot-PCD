package kahoot.server;

import kahoot.game.*;
import kahoot.messages.*;
import kahoot.coordination.*;
import java.util.*;
import java.util.concurrent.*;

public class GameState {
    private final Game game;
    private final Map<String, DealWithClient> connectedClients;
    private final Map<Integer, ModifiedCountdownLatch> individualLatches;
    private final Map<String, TeamBarrier> teamBarriers;
    private final Map<Integer, Map<String, Integer>> questionAnswers; // questionIndex -> team -> answer

    private Question currentQuestion;
    private Timer questionTimer;
    private boolean gameInProgress;

    //Possibilidade de multiplos jogos em simultaneo
    public GameState(String gameId, int numTeams, int playersPerTeam, int numQuestions) {
        this.game = new Game(gameId, numTeams, playersPerTeam, numQuestions);
        this.connectedClients = new ConcurrentHashMap<>();
        this.individualLatches = new ConcurrentHashMap<>();
        this.teamBarriers = new ConcurrentHashMap<>();
        this.questionAnswers = new ConcurrentHashMap<>();
        this.gameInProgress = false;

        // Inicializar barreiras para cada equipa
        for (String teamId : game.getTeams().keySet()) {
            teamBarriers.put(teamId, new TeamBarrier(playersPerTeam, 30000)); // 30 segundos
        }
    }

    public synchronized boolean addPlayer(String teamId, String username, DealWithClient client) {
        if (gameInProgress || game.isGameEnded()) {
            return false;
        }

        boolean success = game.addPlayer(teamId, username);
        if (success) {
            connectedClients.put(username, client);
            System.out.println("Jogador " + username + " juntou-se à equipa " + teamId);

            // Verificar se podemos iniciar o jogo
            if (game.canStartGame() && !gameInProgress) {
                startGame();
            }
        }
        return success;
    }

    private void startGame() {
        gameInProgress = true;
        game.setGameStarted(true);
        System.out.println("Jogo " + game.getGameId() + " iniciado!");

        // Enviar primeira pergunta
        sendNextQuestion();
    }

    private void sendNextQuestion() {
        currentQuestion = game.getQuiz().getNextQuestion();
        if (currentQuestion == null) {
            endGame();
            return;
        }

        int questionIndex = game.getCurrentQuestionIndex();
        boolean isTeamQuestion = (questionIndex % 2 == 1); // Ímpares são individuais, pares são equipa

        // Configurar estruturas de coordenação
        if (isTeamQuestion) {
            // Pergunta de equipa - reset das barreiras
            for (TeamBarrier barrier : teamBarriers.values()) {
                barrier.reset();
            }
        } else {
            // Pergunta individual - criar latch
            int totalPlayers = game.getTeams().values().stream()
                    .mapToInt(Team::getPlayerCount)
                    .sum();
            individualLatches.put(questionIndex, new ModifiedCountdownLatch(2, 2, 30000, totalPlayers));
        }

        // Inicializar estrutura para respostas
        questionAnswers.put(questionIndex, new ConcurrentHashMap<>());

        // Enviar pergunta a todos os jogadores
        broadcastQuestion(currentQuestion, questionIndex, isTeamQuestion);

        // Iniciar temporizador
        startQuestionTimer(questionIndex);
    }

    private void broadcastQuestion(Question question, int questionIndex, boolean isTeamQuestion) {
        QuestionMessage msg = new QuestionMessage(
                game.getGameId(), "", "", question, 30, questionIndex, isTeamQuestion
        );

        // ENVIA PARA TODOS OS CLIENTES CONECTADOS A ESTE JOGO
        for (DealWithClient client : connectedClients.values()) {
            client.sendMessage(msg);
        }
    }

        // Iniciar temporizador para a pergunta do synchronized ??
    private void startQuestionTimer(int questionIndex) {
        questionTimer = new Timer();
        questionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameState.this) {
                    endQuestion(questionIndex);
                }
            }
        }, 30000); // 30 segundos
    }

    public synchronized void processAnswer(AnswerMessage answerMsg) {
        if (!gameInProgress || game.isGameEnded()) {
            return;
        }

        int questionIndex = answerMsg.getQuestionIndex();
        String username = answerMsg.getUsername();
        String teamId = answerMsg.getTeamId();
        int answer = answerMsg.getAnswer();


        Question question = getCurrentQuestion();

        // VALIDAR SE A PERGUNTA EXISTE
        if (question == null) {
            System.err.println(" ERRO: Pergunta é null para o índice: " + questionIndex);
            return;
        }

        // Registar resposta
        questionAnswers.get(questionIndex).put(teamId, answer);
        boolean isCorrect = question.isCorrect(answer);
        boolean isTeamQuestion = (questionIndex % 2 == 1);

        if (isTeamQuestion) {
            processTeamAnswer(teamId, username, question, isCorrect, questionIndex);
        } else {
            processIndividualAnswer(teamId, username, question, isCorrect, questionIndex);
        }

        // Verificar se todas as respostas foram recebidas
        if (allAnswersReceived(questionIndex)) {
            endQuestion(questionIndex);
        }
    }

    private void processIndividualAnswer(String teamId, String username, Question question,
                                         boolean isCorrect, int questionIndex) {
        if (isCorrect) {
            ModifiedCountdownLatch latch = individualLatches.get(questionIndex);
            int bonus = latch.countDown();
            int points = question.getPoints() * bonus;

            // Atualizar pontuação do jogador e da equipa
            updateScores(teamId, username, points);

            System.out.println(username + " (" + teamId + ") ganhou " + points + " pontos (bónus: " + bonus + ")");
        }
    }

    private void processTeamAnswer(String teamId, String username, Question question,
                                   boolean isCorrect, int questionIndex) {
        try {
            TeamBarrier barrier = teamBarriers.get(teamId);
            int position = barrier.await();

            if (position == 1) {
                // Primeiro jogador a chegar - calcular pontuação da equipa
                calculateTeamScore(teamId, question, questionIndex);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void calculateTeamScore(String teamId, Question question, int questionIndex) {
        Map<String, Integer> teamAnswers = questionAnswers.get(questionIndex);
        Team team = game.getTeams().get(teamId);

        boolean allCorrect = true;
        int correctCount = 0;

        // ✅ VERIFICAR CADA JOGADOR DA EQUIPA INDIVIDUALMENTE
        for (Player player : team.getPlayers()) {
            String playerKey = teamId + "_" + player.getUsername();
            Integer answer = teamAnswers.get(playerKey);

            if (answer == null) {
                allCorrect = false;
                System.out.println("O" + player.getUsername() + " não respondeu");
            } else if (!question.isCorrect(answer)) {
                allCorrect = false;
                System.out.println("O" + player.getUsername() + " respondeu errado: " + answer);
            } else {
                correctCount++;
                System.out.println("O" + player.getUsername() + " respondeu corretamente");
            }
        }

        int points;
        if (allCorrect) {
            points = question.getPoints() * 2; // Dobro da pontuação
            System.out.println("🎯 Equipa " + teamId + " ganhou " + points + " pontos (TODOS acertaram)");
        } else if (correctCount > 0) {
            points = question.getPoints(); // Pontuação normal
            System.out.println("🎯 Equipa " + teamId + " ganhou " + points + " pontos (" + correctCount + "/" + team.getPlayers().size() + " acertaram)");
        } else {
            points = 0;
            System.out.println("💥 Equipa " + teamId + " não ganhou pontos (ninguém acertou)");
        }

        team.addTeamScore(points);
    }

    private void updateScores(String teamId, String username, int points) {
        Team team = game.getTeams().get(teamId);
        for (Player player : team.getPlayers()) {
            if (player.getUsername().equals(username)) {
                player.addScore(points);
                break;
            }
        }
        team.addTeamScore(points);
    }

    private boolean allAnswersReceived(int questionIndex) {
        int totalPlayers = game.getTeams().values().stream()
                .mapToInt(Team::getPlayerCount)
                .sum();
        return questionAnswers.get(questionIndex).size() >= totalPlayers;
    }

    private void endQuestion(int questionIndex) {
        if (questionTimer != null) {
            questionTimer.cancel();
        }

        // Enviar pontuações atualizadas
        broadcastScores(questionIndex);

        // Preparar próxima pergunta após pequena pausa
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameState.this) {
                    game.incrementQuestion();
                    sendNextQuestion();
                }
            }
        }, 5000); // 5 segundos de pausa
    }

    private void broadcastScores(int questionIndex) {
        Map<String, Integer> teamScores = new HashMap<>();
        for (Team team : game.getTeams().values()) {
            teamScores.put(team.getTeamId(), team.getTeamScore());
        }

        for (DealWithClient client : connectedClients.values()) {
            int currentRoundPoints = 0;         // VERIFICAR COMO POR PONTOS POR RONDA

            ScoreMessage scoreMsg = new ScoreMessage(
                    game.getGameId(), "", "", teamScores, currentRoundPoints, questionIndex
            );
            client.sendMessage(scoreMsg);
        }
    }

    private void endGame() {
        gameInProgress = false;
        game.setGameEnded(true);

        // Calcular equipa vencedora
        String winningTeam = "";
        int maxScore = -1;
        Map<String, Integer> finalScores = new HashMap<>();

        for (Team team : game.getTeams().values()) {
            finalScores.put(team.getTeamId(), team.getTeamScore());
            if (team.getTeamScore() > maxScore) {
                maxScore = team.getTeamScore();
                winningTeam = team.getTeamId();
            }
        }

        // Enviar resultados finais
        for (DealWithClient client : connectedClients.values()) {
            GameEndMessage endMsg = new GameEndMessage(
                    game.getGameId(), "", "", finalScores, winningTeam
            );
            client.sendMessage(endMsg);
        }

        System.out.println("Jogo " + game.getGameId() + " terminado! Vencedor: " + winningTeam);
    }

    private Question getCurrentQuestion() {
        if (currentQuestion == null) {
            System.err.println(" CurrentQuestion é null");
        }
        return currentQuestion;
    }

    public int getPlayerCount() {
        return connectedClients.size();
    }

}