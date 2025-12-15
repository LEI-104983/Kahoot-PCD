package kahoot.server;

import kahoot.game.*;
import kahoot.messages.*;
import kahoot.coordination.*;
import java.util.*;
import java.util.concurrent.*;

public class GameHandler {
    private final Game game;
    private final Map<String, DealWithClient> connectedClients;
    private final Map<Integer, ModifiedCountdownLatch> individualLatches;
    private final Map<String, TeamBarrier> teamBarriers;
    private final Map<Integer, Map<String, Integer>> questionAnswers; // questionIndex -> playerKey -> resposta
    private final Map<Integer, Set<String>> answeredPlayers; // questionIndex -> conjunto de jogadores que responderam
    private final GameServer server; // Referência ao servidor para limpeza
    private final Map<Integer, Map<String, Integer>> roundPoints; // questionIndex -> teamId -> pontos ganhos nesta ronda

    private Question currentQuestion;
    private Timer questionTimer;
    private boolean gameInProgress;
    private final Map<Integer, Boolean> questionEnded; // questionIndex -> flag de pergunta terminada
    private final Map<Integer, Question> questionsByIndex; // questionIndex -> Question

    public GameHandler(String gameId, int numTeams, int playersPerTeam, int numQuestions, GameServer server) {
        this.game = new Game(gameId, numTeams, playersPerTeam, numQuestions);
        this.server = server;
        this.connectedClients = new ConcurrentHashMap<>();
        this.individualLatches = new ConcurrentHashMap<>();
        this.teamBarriers = new ConcurrentHashMap<>();
        this.questionAnswers = new ConcurrentHashMap<>();
        this.answeredPlayers = new ConcurrentHashMap<>();
        this.questionEnded = new ConcurrentHashMap<>();
        this.roundPoints = new ConcurrentHashMap<>();
        this.questionsByIndex = new ConcurrentHashMap<>();
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
        boolean isTeamQuestion = (questionIndex % 2 == 1); // Índice 0,2,4... = individual; 1,3,5... = equipa

        // Configurar estruturas de coordenação
        if (isTeamQuestion) {
            // Pergunta de equipa - reset das barreiras
            for (TeamBarrier barrier : teamBarriers.values()) {
                barrier.reset();
            }
        } else {
            // Pergunta individual - criar ou resetar latch
            int totalPlayers = game.getTeams().values().stream()
                    .mapToInt(Team::getPlayerCount)
                    .sum();
            ModifiedCountdownLatch latch = individualLatches.get(questionIndex);
            if (latch != null) {
                latch.reset(totalPlayers);
            } else {
                individualLatches.put(questionIndex, new ModifiedCountdownLatch(2, 2, 30000, totalPlayers));
            }
        }

        // Guardar pergunta por índice
        questionsByIndex.put(questionIndex, currentQuestion);
        
        // Inicializar estruturas para respostas
        questionAnswers.put(questionIndex, new ConcurrentHashMap<>());
        answeredPlayers.put(questionIndex, ConcurrentHashMap.newKeySet());
        questionEnded.put(questionIndex, false);
        roundPoints.put(questionIndex, new ConcurrentHashMap<>());

        // Enviar pergunta a todos os jogadores
        broadcastQuestion(currentQuestion, questionIndex, isTeamQuestion);

        // Iniciar temporizador
        startQuestionTimer(questionIndex);
    }

    private void broadcastQuestion(Question question, int questionIndex, boolean isTeamQuestion) {
        QuestionMessage msg = new QuestionMessage(
                game.getGameId(), "", "", question, 30, questionIndex, isTeamQuestion
        );

        for (DealWithClient client : connectedClients.values()) {
            client.sendMessage(msg);
        }
    }

    private void startQuestionTimer(int questionIndex) {
        // Cancelar timer anterior se existir
        if (questionTimer != null) {
            questionTimer.cancel();
        }
        
        questionTimer = new Timer();
        questionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (GameHandler.this) {
                    Boolean ended = questionEnded.get(questionIndex);
                    if (ended != null && !ended) {
                        questionEnded.put(questionIndex, true);
                        endQuestion(questionIndex);
                    }
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

        // Verificar se a pergunta já terminou
        Boolean ended = questionEnded.get(questionIndex);
        if (ended == null || ended) {
            return; // Pergunta já terminou, ignorar resposta tardia
        }

        // Verificar se o jogador já respondeu (prevenir duplicados)
        Set<String> answered = answeredPlayers.get(questionIndex);
        if (answered == null) {
            return; // Pergunta não existe mais
        }
        
        String playerKey = teamId + "_" + username;
        if (!answered.add(playerKey)) {
            // Jogador já respondeu, ignorar resposta duplicada
            return;
        }

        // Obter a pergunta correta para este índice
        Question question = questionsByIndex.get(questionIndex);
        if (question == null) {
            System.err.println(" ERRO: Pergunta é null para o índice: " + questionIndex);
            return;
        }

        boolean isTeamQuestion = (questionIndex % 2 == 1);
        
        // Armazenar resposta - sempre por playerKey para consistência
        questionAnswers.get(questionIndex).put(playerKey, answer);
        boolean isCorrect = question.isCorrect(answer);

        if (isTeamQuestion) {
            processTeamAnswer(teamId, username, question, isCorrect, questionIndex);
        } else {
            processIndividualAnswer(teamId, username, question, isCorrect, questionIndex);
        }

        // Verificar se todas as respostas foram recebidas
        if (allAnswersReceived(questionIndex)) {
            // Verificar novamente dentro do synchronized para garantir execução única
            Boolean stillEnded = questionEnded.get(questionIndex);
            if (stillEnded != null && !stillEnded) {
                questionEnded.put(questionIndex, true);
                endQuestion(questionIndex);
            }
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
            
            // Guardar pontos ganhos nesta ronda
            roundPoints.get(questionIndex).put(teamId, 
                roundPoints.get(questionIndex).getOrDefault(teamId, 0) + points);

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

        //  VERIFICAR CADA JOGADOR DA EQUIPA INDIVIDUALMENTE
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
        
        // Guardar pontos ganhos nesta ronda
        roundPoints.get(questionIndex).put(teamId, points);
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
        Set<String> answered = answeredPlayers.get(questionIndex);
        if (answered == null) {
            return false;
        }
        return answered.size() >= totalPlayers;
    }

    private void endQuestion(int questionIndex) {
        // Cancelar timer se ainda estiver ativo
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }

        // Quebrar barreiras de equipa para desbloquear threads bloqueadas
        boolean isTeamQuestion = (questionIndex % 2 == 1);
        if (isTeamQuestion) {
            for (TeamBarrier barrier : teamBarriers.values()) {
                barrier.breakBarrier();
            }
        }

        // Marcar pergunta como terminada
        questionEnded.put(questionIndex, true);

        // Enviar pontuações atualizadas
        broadcastScores(questionIndex);

        // Preparar próxima pergunta
        synchronized (this) {
            if (!game.isGameEnded()) {
                game.incrementQuestion();
                sendNextQuestion();
            }
        }
    }

    private void broadcastScores(int questionIndex) {
        Map<String, Integer> teamScores = new HashMap<>();
        for (Team team : game.getTeams().values()) {
            teamScores.put(team.getTeamId(), team.getTeamScore());
        }

        Map<String, Integer> pointsForRound = roundPoints.getOrDefault(questionIndex, new HashMap<>());
        
        for (DealWithClient client : connectedClients.values()) {
            String clientTeamId = getClientTeamId(client);
            int currentRoundPoints = pointsForRound.getOrDefault(clientTeamId, 0);

            ScoreMessage scoreMsg = new ScoreMessage(
                    game.getGameId(), "", "", teamScores, currentRoundPoints, questionIndex
            );
            client.sendMessage(scoreMsg);
        }
    }
    
    private String getClientTeamId(DealWithClient client) {
        for (Team team : game.getTeams().values()) {
            for (Player player : team.getPlayers()) {
                if (player.getUsername().equals(client.getUsername())) {
                    return team.getTeamId();
                }
            }
        }
        return "";
    }

    private void endGame() {
        gameInProgress = false;
        game.setGameEnded(true);
        
        // Quebrar todas as barreiras para desbloquear threads bloqueadas
        for (TeamBarrier barrier : teamBarriers.values()) {
            barrier.breakBarrier();
        }

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