package kahoot.server;

import kahoot.messages.*;
import java.io.*;
import java.net.*;

public class DealWithClient implements Runnable {
    private final Socket clientSocket;
    private final GameServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private String gameId;
    private boolean connected;

    public DealWithClient(Socket socket, GameServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.connected = true;
        
        // Configurar timeout de conexão (60 segundos)
        try {
            socket.setSoTimeout(60000);
        } catch (SocketException e) {
            System.err.println("Erro ao configurar timeout: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            in = new ObjectInputStream(clientSocket.getInputStream());

            while (connected) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof EnrollmentMessage) {
                        handleEnrollment((EnrollmentMessage) obj);
                    } else if (obj instanceof AnswerMessage) {
                        handleAnswer((AnswerMessage) obj);
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout - cliente inativo, mas manter conexão
                    // TODO: Pode ser usado para heartbeat no futuro
                    continue;
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Cliente desconectado: " + (username != null ? username : "Unknown"));
        } finally {
            disconnect();
        }
    }

    private void handleEnrollment(EnrollmentMessage msg) {
        this.gameId = msg.getGameId();
        this.username = msg.getUsername();

        GameState game = server.getGame(gameId);
        if (game == null) {
            sendMessage(new ErrorMessage(gameId, "", username, "Jogo não encontrado: " + gameId));
            disconnect();
            return;
        }

        boolean success = game.addPlayer(msg.getTeamId(), username, this);
        if (!success) {
            sendMessage(new ErrorMessage(gameId, msg.getTeamId(), username,
                    "Não foi possível juntar-se ao jogo. Equipa cheia ou username duplicado."));
            disconnect();
        } else {
            sendMessage(new ErrorMessage(gameId, msg.getTeamId(), username, "SUCCESS: Juntou-se à equipa " + msg.getTeamId()));
        }
    }

    private void handleAnswer(AnswerMessage msg) {
        GameState game = server.getGame(gameId);
        if (game != null) {
            game.processAnswer(msg);
        }
    }

    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.out.println("Erro ao enviar mensagem para " + username);
            disconnect();
        }
    }

    private void disconnect() {
        connected = false;
        
        // Notificar GameHandler sobre desconexão
        if (gameId != null && username != null) {
            GameState game = server.getGame(gameId);
            if (game != null) {
                game.removePlayer(username);
            }
        }
        
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException e) {
            // Ignorar
        }
    }

    public String getUsername() {
        return username;
    }
}