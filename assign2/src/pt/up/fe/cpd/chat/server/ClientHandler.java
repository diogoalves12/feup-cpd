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
    private ClientConnection currentConnection;

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
            cleanupConnection();
            System.out.printf("Closed connection from %s%n", socket.getRemoteSocketAddress());
        }
    }

    private void handleCommand(ClientCommand command, PrintWriter writer) {
        switch (command.type()) {
            case REGISTER -> handleRegister(command.arguments(), writer);
            case LOGIN -> handleLogin(command.arguments(), writer);
            case RESUME -> handleResume(command.arguments(), writer);
            case LIST_ROOMS -> handleListRooms(writer);
            case CREATE_ROOM -> handleCreateRoom(command.arguments(), writer);
            case JOIN -> handleJoin(command.arguments(), writer);
            case LEAVE -> handleLeave(writer);
            case MSG -> handleMessage(command.arguments(), writer);
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
        installConnection(writer);
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
        installConnection(writer);
    }

    private void handleListRooms(PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        reply(writer, Protocol.rooms(serverState.listRoomNames()));
    }

    private void handleCreateRoom(List<String> arguments, PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        String roomName = arguments.getFirst().trim();
        if (roomName.isBlank()) {
            reply(writer, Protocol.error("Room name must not be blank"));
            return;
        }

        if (!serverState.createRoom(roomName)) {
            reply(writer, Protocol.error("Room already exists"));
            return;
        }

        reply(writer, Protocol.ok(CommandType.CREATE_ROOM.name()));
    }

    private void handleJoin(List<String> arguments, PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        String roomName = arguments.getFirst().trim();
        if (roomName.isBlank()) {
            writer.println(Protocol.error("Room name must not be blank"));
            return;
        }

        serverState.joinRoom(currentSession, roomName);
        reply(writer, Protocol.ok(CommandType.JOIN.name()));
        serverState.broadcastSystemMessage(roomName, currentSession.username() + " entered the room");
    }

    private void handleLeave(PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        String roomName = currentSession.currentRoom();
        if (roomName == null) {
            reply(writer, Protocol.error("Not in a room"));
            return;
        }

        serverState.broadcastSystemMessage(roomName, currentSession.username() + " left the room");
        if (!serverState.leaveRoom(currentSession)) {
            reply(writer, Protocol.error("Not in a room"));
            return;
        }

        reply(writer, Protocol.ok(CommandType.LEAVE.name()));
    }

    private void handleMessage(List<String> arguments, PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        if (currentSession.currentRoom() == null) {
            reply(writer, Protocol.error("Not in a room"));
            return;
        }

        serverState.broadcastRoomMessage(currentSession, arguments.getFirst());
    }

    private boolean requireAuthentication(PrintWriter writer) {
        if (!isAuthenticated()) {
            writer.println(Protocol.error("Authentication required"));
            return false;
        }

        return true;
    }

    private boolean isAuthenticated() {
        return currentSession != null;
    }

    private void installConnection(PrintWriter writer) {
        ClientConnection newConnection = new ClientConnection(currentSession.username(), writer);
        newConnection.startWriter();
        serverState.attachConnection(currentSession, newConnection);

        if (currentConnection != null && currentConnection != newConnection) {
            currentConnection.close();
        }
        currentConnection = newConnection;
    }

    private void cleanupConnection() {
        if (currentSession != null && currentConnection != null) {
            serverState.detachConnection(currentSession, currentConnection);
            currentConnection.close();
        }
    }

    private void reply(PrintWriter writer, String message) {
        if (currentConnection != null && currentConnection.isActive()) {
            currentConnection.send(message);
            return;
        }

        writer.println(message);
    }
}
