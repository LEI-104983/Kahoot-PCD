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
    private final Map<Integer, Map<String, Integer>> questionAnswers; // questionIndex -> playerKey -> resposta
    private final Map<Integer, Set<String>> answeredPlayers; // questionIndex -> conjunto de jogadores que responderam
    private final GameServer server; // Referência ao servidor para limpeza
    private final Map<Integer, Thread> awaitThreads; // questionIndex -> thread que chama await()

    private Question currentQuestion;
    private Timer questionTimer;
    private boolean gameInProgress;
    private final Map<Integer, Boolean> questionEnded; // questionIndex -> already ended flag

    public GameState(String gameId, int numTeams, int playersPerTeam, int numQuestions, GameServer server) {
        this.game = new Game(gameId, numTeams, playersPerTeam, numQuestions);
        this.server = server;
        this.connectedClients = new ConcurrentHashMap<>();
        this.individualLatches = new ConcurrentHashMap<>();
        this.teamBarriers = new ConcurrentHashMap<>();
        this.questionAnswers = new ConcurrentHashMap<>();
        this.answeredPlayers = new ConcurrentHashMap<>();
        this.questionEnded = new ConcurrentHashMap<>();
        this.awaitThreads = new ConcurrentHashMap<>();
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

    // Método para notificar desconexão de jogador
    public synchronized void removePlayer(String username) {
        connectedClients.remove(username);
        System.out.println("Jogador " + username + " desconectou-se");
        // Nota: Não removemos do Game para manter consistência, mas podemos ajustar contagens se necessário
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
                individualLatches.put(questionIndex, new ModifiedCountdownLatch(2, 2, totalPlayers));
            }
            
            // Criar thread que chama await() para esperar até todos responderem
            startAwaitThread(questionIndex);
        }

        // Inicializar estruturas para respostas
        questionAnswers.put(questionIndex, new ConcurrentHashMap<>());
        answeredPlayers.put(questionIndex, ConcurrentHashMap.newKeySet());
        questionEnded.put(questionIndex, false);

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

    private void startAwaitThread(int questionIndex) {
        // Cancelar thread anterior se existir
        Thread previousThread = awaitThreads.get(questionIndex);
        if (previousThread != null && previousThread.isAlive()) {
            previousThread.interrupt();
        }
        
        ModifiedCountdownLatch latch = individualLatches.get(questionIndex);
        Thread awaitThread = new Thread(() -> {
            try {
                // Thread bloqueia aqui até o contador chegar a zero
                latch.await();
                
                // Quando o contador chegar a zero (todos responderam), terminar a pergunta
                synchronized (GameState.this) {
                    Boolean ended = questionEnded.get(questionIndex);
                    if (ended != null && !ended) {
                        questionEnded.put(questionIndex, true);
                        endQuestion(questionIndex);
                    }
                }
            } catch (InterruptedException e) {
                // Thread foi interrompida (timeout ou nova pergunta)
                Thread.currentThread().interrupt();
            }
        });
        
        awaitThread.start();
        awaitThreads.put(questionIndex, awaitThread);
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
                synchronized (GameState.this) {
                    Boolean ended = questionEnded.get(questionIndex);
                    if (ended != null && !ended) {
                        questionEnded.put(questionIndex, true);
                        // Interromper thread await se ainda estiver esperando
                        Thread awaitThread = awaitThreads.get(questionIndex);
                        if (awaitThread != null && awaitThread.isAlive()) {
                            awaitThread.interrupt();
                        }
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

        Question question = getCurrentQuestion();

        // VALIDAR SE A PERGUNTA EXISTE
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
        ModifiedCountdownLatch latch = individualLatches.get(questionIndex);
        
        // TODOS os jogadores chamam countDown() para decrementar o contador
        int bonus = latch.countDown();
        
        // Apenas os que acertam recebem pontos (com bónus se aplicável)
        if (isCorrect) {
            int points = question.getPoints() * bonus;
            
            // Atualizar pontuação do jogador e da equipa
            updateScores(teamId, username, points);
            
            System.out.println(username + " (" + teamId + ") ganhou " + points + " pontos (bónus: " + bonus + ")");
        } else {
            System.out.println(username + " (" + teamId + ") respondeu incorretamente");
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
        
        // Interromper thread await se ainda estiver esperando
        Thread awaitThread = awaitThreads.get(questionIndex);
        if (awaitThread != null && awaitThread.isAlive()) {
            awaitThread.interrupt();
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

        for (DealWithClient client : connectedClients.values()) {
            // Calcular pontos da ronda atual (simplificado)
            int currentRoundPoints = 0; // TODO: Implementar cálculo específico

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