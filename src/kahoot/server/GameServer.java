package kahoot.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {
    private ServerSocket serverSocket;
    private final Map<String, GameHandler> activeGames;
    private final ExecutorService threadPool;
    private boolean running;

    public GameServer(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.activeGames = new HashMap<>();
        this.threadPool = Executors.newFixedThreadPool(10);
        this.running = true;
    }

    public void start() {
        System.out.println("Servidor Kahoot iniciado na porta " + serverSocket.getLocalPort());
        System.out.println("Comandos: new <num_equipas> <jogadores_por_equipa> <num_perguntas>");

        // Thread para aceitar conexões
        new Thread(this::acceptConnections).start();

        // Thread para interface de comandos
        new Thread(this::handleCommands).start();
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nova conexão: " + clientSocket.getInetAddress());
                threadPool.execute(new DealWithClient(clientSocket, this));
            } catch (IOException e) {
                if (running) {
                    System.out.println("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    private void handleCommands() {
        Scanner scanner = new Scanner(System.in);
        while (running) {
            System.out.print("> ");
            String command = scanner.nextLine().trim();

            if (command.startsWith("new")) {
                handleNewGame(command);
            } else if (command.equals("list")) {
                handleListGames();
            } else if (command.equals("exit")) {
                shutdown();
                break;
            } else if (!command.isEmpty()) {
                System.out.println("Comando desconhecido. Use: new, list, exit");
            }
        }
        scanner.close();
    }

    private void handleNewGame(String command) {
        String[] parts = command.split(" ");
        if (parts.length != 4) {
            System.out.println("Uso: new <num_equipas> <jogadores_por_equipa> <num_perguntas>");
            return;
        }

        try {
            int numTeams = Integer.parseInt(parts[1]);
            int playersPerTeam = Integer.parseInt(parts[2]);
            int numQuestions = Integer.parseInt(parts[3]);

            if (numTeams < 1 || playersPerTeam < 1 || numQuestions < 1) {
                System.out.println("Valores devem ser positivos");
                return;
            }

            // GERAR GAME ID MAIS SIMPLES
            String gameId = "game" + (activeGames.size() + 1);
            GameHandler game = new GameHandler(gameId, numTeams, playersPerTeam, numQuestions, this);
            activeGames.put(gameId, game);

            System.out.println("🎮 JOGO CRIADO: " + gameId);
            System.out.println("   Equipas: " + numTeams);
            System.out.println("   Jogadores por equipa: " + playersPerTeam);
            System.out.println("   Perguntas: " + numQuestions);
            System.out.println("   Use este ID nos clientes: " + gameId);

        } catch (NumberFormatException e) {
            System.out.println("Parâmetros inválidos. Use números inteiros.");
        }
    }

    private void handleListGames() {
        if (activeGames.isEmpty()) {
            System.out.println("Nenhum jogo ativo");
            return;
        }

        System.out.println("Jogos ativos:");
        for (String gameId : activeGames.keySet()) {
            GameHandler game = activeGames.get(gameId);
            System.out.println(" - " + gameId + ": " + game.getPlayerCount() + " jogadores conectados");
        }
    }

    public GameHandler getGame(String gameId) {
        return activeGames.get(gameId);
    }

    public void removeGame(String gameId) {
        activeGames.remove(gameId);
    }

    private void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            // Ignorar
        }
        threadPool.shutdown();
        System.out.println("Servidor encerrado");
    }

    public static void main(String[] args) {
        try {
            GameServer server = new GameServer(8080);
            server.start();
        } catch (IOException e) {
            System.out.println("Erro ao iniciar servidor: " + e.getMessage());
        }
    }
}