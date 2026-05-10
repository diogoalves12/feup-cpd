package pt.up.fe.cpd.chat.server;

import pt.up.fe.cpd.chat.protocol.ClientCommand;
import pt.up.fe.cpd.chat.protocol.CommandParser;
import pt.up.fe.cpd.chat.protocol.CommandType;
import pt.up.fe.cpd.chat.protocol.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerState serverState;
    private Session currentSession;

    public ClientHandler(Socket socket, ServerState serverState) {
        this.socket = socket;
        this.serverState = serverState;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.printf("[%s] %s%n", socket.getRemoteSocketAddress(), line);

                final ClientCommand command;
                try {
                    command = CommandParser.parse(line);
                } catch (IllegalArgumentException exception) {
                    writer.println(Protocol.error(exception.getMessage()));
                    continue;
                }

                if (command.type() == CommandType.QUIT) {
                    writer.println(Protocol.ok("bye"));
                    break;
                }

                handleCommand(command, writer);
            }
        } catch (IOException exception) {
            System.err.printf("Connection error with %s: %s%n", socket.getRemoteSocketAddress(), exception.getMessage());
        } finally {
            System.out.printf("Closed connection from %s%n", socket.getRemoteSocketAddress());
        }
    }

    private void handleCommand(ClientCommand command, PrintWriter writer) {
        switch (command.type()) {
            case REGISTER -> handleRegister(command.arguments(), writer);
            case LOGIN -> handleLogin(command.arguments(), writer);
            case RESUME -> handleResume(command.arguments(), writer);
            default -> writer.println(Protocol.ok(command.type().name()));
        }
    }

    private void handleRegister(List<String> arguments, PrintWriter writer) {
        String username = arguments.getFirst().trim();
        String password = arguments.get(1).trim();
        if (username.isBlank() || password.isBlank()) {
            writer.println(Protocol.error("Username and password must not be blank"));
            return;
        }

        String passwordHash = AuthService.hashPassword(password);
        boolean registered = serverState.registerUser(username, passwordHash);
        if (!registered) {
            writer.println(Protocol.error("User already exists"));
            return;
        }

        writer.println(Protocol.ok(CommandType.REGISTER.name()));
    }

    private void handleLogin(List<String> arguments, PrintWriter writer) {
        String username = arguments.getFirst().trim();
        String password = arguments.get(1).trim();
        if (username.isBlank() || password.isBlank()) {
            writer.println(Protocol.error("Invalid username or password"));
            return;
        }

        User user = serverState.findUser(username);
        if (user == null || !AuthService.verifyPassword(password, user.passwordHash())) {
            writer.println(Protocol.error("Invalid username or password"));
            return;
        }

        Session session = serverState.storeSession(TokenService.newSession(username));
        currentSession = session;
        writer.println(Protocol.ok(CommandType.LOGIN.name()));
        writer.println(Protocol.token(session.token(), session.expiresAt()));
    }

    private void handleResume(List<String> arguments, PrintWriter writer) {
        String token = arguments.getFirst().trim();
        if (token.isBlank()) {
            writer.println(Protocol.error("Invalid or expired token"));
            return;
        }

        Session session = serverState.findValidSession(token, Instant.now());
        if (session == null) {
            writer.println(Protocol.error("Invalid or expired token"));
            return;
        }

        currentSession = session;
        writer.println(Protocol.ok(CommandType.RESUME.name()));
    }

    private boolean isAuthenticated() {
        return currentSession != null;
    }
}
