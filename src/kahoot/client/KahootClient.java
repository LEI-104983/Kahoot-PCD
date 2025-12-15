package kahoot.client;

import kahoot.messages.*;
import kahoot.game.Question;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class KahootClient {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String gameId;
    private String teamId;
    private String username;

    private JFrame frame;
    private JLabel questionLabel;
    private JButton[] answerButtons;
    private JLabel timerLabel;
    private JLabel scoreLabel;
    private JTextArea scoreboardArea;
    private JLabel roundLabel;

    private int currentScore = 0;
    private boolean answeringEnabled = false;
    private Thread currentTimerThread = null; // Referência para a thread do timer atual
    private int currentQuestionIndex = -1;

    public KahootClient(String serverIP, int port, String gameId, String teamId, String username) {
        this.gameId = gameId;
        this.teamId = teamId;
        this.username = username;

        initializeGUI();
        connectToServer(serverIP, port);
    }

    private void initializeGUI() {
        frame = new JFrame("Kahoot - " + username + " (" + teamId + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(10, 10));
        frame.setSize(800, 600);

        // Painel superior
        JPanel topPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        topPanel.add(new JLabel("Jogador: " + username, SwingConstants.CENTER));
        topPanel.add(new JLabel("Equipa: " + teamId, SwingConstants.CENTER));

        scoreLabel = new JLabel("Pontuação: 0", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 14));
        topPanel.add(scoreLabel);

        roundLabel = new JLabel("Ronda: -", SwingConstants.CENTER);
        topPanel.add(roundLabel);

        frame.add(topPanel, BorderLayout.NORTH);

        // Painel central com pergunta
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        questionLabel = new JLabel("Aguardando o início do jogo...", SwingConstants.CENTER);
        questionLabel.setFont(new Font("Arial", Font.BOLD, 18));
        questionLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 40, 20));
        centerPanel.add(questionLabel, BorderLayout.NORTH);

        // Painel de respostas
        JPanel answersPanel = new JPanel(new GridLayout(2, 2, 15, 15));
        answersPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        answerButtons = new JButton[4];

        Color[] colors = {new Color(255, 80, 80), new Color(80, 120, 255),
                new Color(255, 180, 0), new Color(100, 200, 100)};
        String[] labels = {"A", "B", "C", "D"};

        for (int i = 0; i < 4; i++) {
            answerButtons[i] = new JButton();
            answerButtons[i].setBackground(colors[i]);
            answerButtons[i].setForeground(Color.WHITE);
            answerButtons[i].setFont(new Font("Arial", Font.BOLD, 16));
            answerButtons[i].setEnabled(false);
            answerButtons[i].setFocusPainted(false);
            answerButtons[i].setBorder(BorderFactory.createRaisedBevelBorder());

            final int answerIndex = i;
            answerButtons[i].addActionListener(e -> sendAnswer(answerIndex));

            answersPanel.add(answerButtons[i]);
        }

        centerPanel.add(answersPanel, BorderLayout.CENTER);

        // Timer
        timerLabel = new JLabel("Tempo: --", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 20));
        timerLabel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        centerPanel.add(timerLabel, BorderLayout.SOUTH);

        frame.add(centerPanel, BorderLayout.CENTER);

        // Painel inferior com placar
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createTitledBorder("Placar"));

        scoreboardArea = new JTextArea(8, 20);
        scoreboardArea.setEditable(false);
        scoreboardArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        scoreboardArea.setText("Aguardando jogo...\n");

        JScrollPane scrollPane = new JScrollPane(scoreboardArea);
        bottomPanel.add(scrollPane, BorderLayout.CENTER);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }



    private void connectToServer(String serverIP, int port) {
        try {
            socket = new Socket(serverIP, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Enviar mensagem de inscrição
            EnrollmentMessage enrollMsg = new EnrollmentMessage(gameId, teamId, username);
            out.writeObject(enrollMsg);
            out.flush();

            // Iniciar thread para receber mensagens
            new Thread(this::receiveMessages).start();

            updateStatus("Conectado ao servidor. Aguardando jogadores...");

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                    "Erro ao conectar ao servidor: " + e.getMessage(),
                    "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }



    private void receiveMessages() {
        try {
            while (true) {
                Object obj = in.readObject();
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (obj instanceof QuestionMessage) {
                            handleQuestionMessage((QuestionMessage) obj);
                        } else if (obj instanceof ScoreMessage) {
                            handleScoreMessage((ScoreMessage) obj);
                        } else if (obj instanceof GameEndMessage) {
                            handleGameEndMessage((GameEndMessage) obj);
                        } else if (obj instanceof ErrorMessage) {
                            handleErrorMessage((ErrorMessage) obj);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException | ClassNotFoundException e) {
            SwingUtilities.invokeLater(() -> {
                updateStatus("Conexão com o servidor perdida.");
            });
        }
    }

    private void handleQuestionMessage(QuestionMessage msg) {
        Question question = msg.getQuestion();
        currentQuestionIndex = msg.getQuestionIndex();
        roundLabel.setText("Ronda: " + (currentQuestionIndex + 1));

        // Parar timer anterior se existir
        stopTimer();

        questionLabel.setText("<html><div style='text-align: center;'>" +
                question.getQuestion() + "</div></html>");

        String[] options = question.getOptions();
        for (int i = 0; i < 4; i++) {
            if (i < options.length) {
                answerButtons[i].setText("<html><center>" + options[i] + "</center></html>");
                answerButtons[i].setEnabled(true);
            } else {
                answerButtons[i].setText("");
                answerButtons[i].setEnabled(false);
            }
        }

        answeringEnabled = true;
        startTimer(msg.getTimeLimit());

        updateStatus("Pergunta " + (currentQuestionIndex + 1) +
                (msg.isTeamQuestion() ? " (Equipa)" : " (Individual)"));
    }

    private void stopTimer() {
        if (currentTimerThread != null && currentTimerThread.isAlive()) {
            currentTimerThread.interrupt();
            currentTimerThread = null;
        }
    }

    private void startTimer(int seconds) {
        // Parar timer anterior se existir
        stopTimer();

        // Criar nova thread do timer
        currentTimerThread = new Thread(() -> {
            Thread timerThread = Thread.currentThread();
            for (int i = seconds; i >= 0; i--) {
                // Verificar se foi interrompido antes de atualizar interface
                if (timerThread.isInterrupted()) {
                    return; // Sair se foi interrompido
                }

                final int timeLeft = i;
                final boolean wasInterrupted = timerThread.isInterrupted();
                
                SwingUtilities.invokeLater(() -> {
                    // Só atualizar se não foi interrompido
                    if (!wasInterrupted && currentTimerThread == timerThread) {
                        timerLabel.setText("Tempo: " + timeLeft + "s");
                        if (timeLeft <= 10) {
                            timerLabel.setForeground(Color.RED);
                            timerLabel.setFont(new Font("Arial", Font.BOLD, 22));
                        } else if (timeLeft <= 20) {
                            timerLabel.setForeground(Color.ORANGE);
                            timerLabel.setFont(new Font("Arial", Font.BOLD, 20));
                        } else {
                            timerLabel.setForeground(Color.BLACK);
                            timerLabel.setFont(new Font("Arial", Font.BOLD, 20));
                        }
                    }
                });

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Timer foi interrompido (nova pergunta chegou)
                    return;
                }
            }

            // Timer terminou naturalmente (não foi interrompido)
            if (!timerThread.isInterrupted() && currentTimerThread == timerThread) {
                SwingUtilities.invokeLater(() -> {
                    answeringEnabled = false;
                    for (JButton button : answerButtons) {
                        button.setEnabled(false);
                    }
                    timerLabel.setText("Tempo esgotado!");
                    timerLabel.setForeground(Color.RED);
                });
            }
        });
        
        currentTimerThread.start();
    }

    private void sendAnswer(int answerIndex) {
        if (!answeringEnabled) return;

        try {
            // Desativar botões após resposta
            answeringEnabled = false;
            for (JButton button : answerButtons) {
                button.setEnabled(false);
            }

            // Parar timer (já respondeu)
            stopTimer();

            // Destacar resposta selecionada
            answerButtons[answerIndex].setBackground(answerButtons[answerIndex].getBackground().brighter());

            AnswerMessage answerMsg = new AnswerMessage(gameId, teamId, username, currentQuestionIndex, answerIndex);
            out.writeObject(answerMsg);
            out.flush();

            updateStatus("Resposta enviada: " + (char)('A' + answerIndex));

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Erro ao enviar resposta");
        }
    }

    private void handleScoreMessage(ScoreMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== PLACAR - Ronda ").append(msg.getQuestionIndex() + 1).append(" ===\n\n");

        // Ordenar equipas por pontuação
        msg.getTeamScores().entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> {
                    String teamDisplay = entry.getKey().equals(teamId) ?
                            ">> " + entry.getKey() + " <<" : entry.getKey();
                    sb.append(String.format("%-15s: %3d pontos\n", teamDisplay, entry.getValue()));
                });

        sb.append("\nEsta ronda: +").append(msg.getCurrentRoundPoints()).append(" pontos");

        scoreboardArea.setText(sb.toString());

        // Atualizar pontuação atual se for da nossa equipa
        Integer myScore = msg.getTeamScores().get(teamId);
        if (myScore != null) {
            currentScore = myScore;
            scoreLabel.setText("Pontuação: " + currentScore);
        }
    }

    private void handleGameEndMessage(GameEndMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== JOGO TERMINADO ===\n\n");
        sb.append("EQUIPA VENCEDORA: ").append(msg.getWinningTeam()).append("\n\n");
        sb.append("PONTUAÇÕES FINAIS:\n");

        msg.getFinalScores().entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(entry -> {
                    String teamDisplay = entry.getKey().equals(teamId) ?
                            ">> " + entry.getKey() + " <<" : entry.getKey();
                    sb.append(String.format("%-15s: %3d pontos\n", teamDisplay, entry.getValue()));
                });

        scoreboardArea.setText(sb.toString());

        questionLabel.setText("Jogo Terminado!");
        timerLabel.setText("Vencedor: " + msg.getWinningTeam());

        for (JButton button : answerButtons) {
            button.setEnabled(false);
        }

        JOptionPane.showMessageDialog(frame,
                "Jogo terminado!\n" +
                        "Equipa vencedora: " + msg.getWinningTeam() + "\n" +
                        "Sua pontuação final: " + currentScore,
                "Fim do Jogo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleErrorMessage(ErrorMessage msg) {
        if (msg.getErrorMessage().startsWith("SUCCESS")) {
            updateStatus(msg.getErrorMessage());
        } else {
            JOptionPane.showMessageDialog(frame, msg.getErrorMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatus(String status) {
        System.out.println(username + ": " + status);
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("Uso: java KahootClient <IP> <PORT> <GAME_ID> <TEAM_ID> <USERNAME>");
            System.out.println("Exemplo: java KahootClient localhost 8080 game0 Team1 Player1");
            return;
        }

        String ip = args[0];
        int port = Integer.parseInt(args[1]);
        String gameId = args[2];
        String teamId = args[3];
        String username = args[4];

        SwingUtilities.invokeLater(() -> {
            new KahootClient(ip, port, gameId, teamId, username);
        });
    }
}