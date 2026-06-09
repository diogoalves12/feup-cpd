package pt.up.fe.cpd.chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/* Ponto de entrada do servidor, abre o ServerSocket e fica em loop infinito a aceitar ligacoes TCP (garantem a entrega e a ordem).
 * Cada ligacao (cliente) é delegada a uma virtual thread com um ClientHandler independente.
 */
public final class ChatServer {
    private final int port;
    private final ServerState serverState;

    public ChatServer(int port) {
        this.port = port;
        this.serverState = new ServerState();
    }

    public void start() throws IOException {    
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.printf("ChatServer listening on port %d%n", port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.printf("Accepted connection from %s%n", clientSocket.getRemoteSocketAddress());
                /*
                * Para cada cliente que liga, criamos uma virtual thread com um ClientHandler.
                *
                * O servidor nunca fica bloqueado num cliente especifico e está sempre disponivel para aceitar novas ligacoes.
                * Virtual threads (Java 21) têm overhead minimo, perfeito para operacoes de rede que passam a maior parte do tempo à espera de dados (I/O-bound).
                */
                Thread.ofVirtual().start(new ClientHandler(clientSocket, serverState));
            }
        }
    }

    public static void main(String[] args) {
        final int port;
        try {
            port = parsePort(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return;
        }

        ChatServer server = new ChatServer(port);

        try {
            server.start();
        } catch (IOException exception) {
            System.err.printf("Server error: %s%n", exception.getMessage());
        }
    }

    private static int parsePort(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: ChatServer <port>");
        }

        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Usage: ChatServer <port>");
        }
    }
}
