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
    private final OllamaClient ollamaClient;
    private Session currentSession;
    private ClientConnection currentConnection;

    public ClientHandler(Socket socket, ServerState serverState) {
        this(socket, serverState, new OllamaClient());
    }

    ClientHandler(Socket socket, ServerState serverState, OllamaClient ollamaClient) {
        this.socket = socket;
        this.serverState = serverState;
        this.ollamaClient = ollamaClient;
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
                    if (isUnknownCommand(exception) && handleTextMessage(line, writer)) {
                        continue;
                    }

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
            case CREATE_AI_ROOM -> handleCreateAiRoom(command.arguments(), writer);
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
        installConnection(writer);
        reply(writer, Protocol.ok(CommandType.LOGIN.name()));
        reply(writer, Protocol.token(session.token(), session.expiresAt()));
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
        installConnection(writer);
        reply(writer, Protocol.ok(CommandType.RESUME.name()));
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

    private void handleCreateAiRoom(List<String> arguments, PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        String roomName = arguments.getFirst().trim();
        String prompt = arguments.get(1).trim();
        if (roomName.isBlank() || prompt.isBlank()) {
            reply(writer, Protocol.error("CREATE_AI_ROOM requires a room name and prompt"));
            return;
        }

        if (!serverState.createAiRoom(roomName, prompt)) {
            reply(writer, Protocol.error("Room already exists"));
            return;
        }

        reply(writer, Protocol.ok(CommandType.CREATE_AI_ROOM.name()));
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

        if (!serverState.joinRoom(currentSession, roomName)) {
            reply(writer, Protocol.error("Room does not exist"));
            return;
        }

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
        handleMessageText(arguments.getFirst(), writer);
    }

    private boolean handleTextMessage(String line, PrintWriter writer) {
        if (!isAuthenticated() || currentSession.currentRoom() == null || line.trim().isEmpty()) {
            return false;
        }

        handleMessageText(line, writer);
        return true;
    }

    private void handleMessageText(String message, PrintWriter writer) {
        if (!requireAuthentication(writer)) {
            return;
        }

        String roomName = currentSession.currentRoom();
        if (roomName == null) {
            reply(writer, Protocol.error("Not in a room"));
            return;
        }

        serverState.broadcastRoomMessage(currentSession, message);
        if (serverState.isAiRoom(roomName)) {
            triggerAiResponse(roomName, message);
        }
    }

    private boolean isUnknownCommand(IllegalArgumentException exception) {
        return "Unknown command".equals(exception.getMessage());
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
        ClientConnection newConnection = new ClientConnection(currentSession.username(), socket, writer);
        newConnection.startWriter();
        serverState.attachConnection(currentSession, newConnection);
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

    private void triggerAiResponse(String roomName, String latestUserMessage) {
        Thread.ofVirtual().start(() -> {
            ServerState.AiRoomContext context = serverState.aiRoomContext(roomName);
            if (context == null) {
                return;
            }

            String prompt = buildAiPrompt(context, latestUserMessage);
            try {
                String botResponse = ollamaClient.generate(prompt);
                serverState.broadcastBotMessage(roomName, botResponse);
            } catch (IOException exception) {
                System.err.printf("AI response failed for room %s: %s%n", roomName, shortReason(exception));
            }
        });
    }

    private String buildAiPrompt(ServerState.AiRoomContext context, String latestUserMessage) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are Bot, a participant in a chat room.\n");
        prompt.append("Use the room prompt and previous messages as context.\n");
        prompt.append("Reply to the latest user message according to the room prompt.\n");
        prompt.append("Do not summarize the conversation unless the room prompt explicitly asks for a summary.\n");
        prompt.append("Return only the message text.\n");
        prompt.append("Do not include prefixes like \"Bot:\" or \"ROOM_MESSAGE\".\n\n");
        prompt.append("Room name:\n");
        prompt.append(context.roomName()).append("\n\n");
        prompt.append("Room prompt:\n");
        prompt.append(context.prompt()).append("\n\n");
        prompt.append("Previous room messages:\n");
        for (String entry : context.messageLog()) {
            prompt.append(entry).append('\n');
        }
        prompt.append("\nLatest user message:\n");
        prompt.append(latestUserMessage).append("\n\n");
        prompt.append("Bot response:\n");
        return prompt.toString();
    }

    private String shortReason(IOException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "service unavailable";
        }

        String normalized = message.replace('\n', ' ').trim();
        if (normalized.length() > 80) {
            return normalized.substring(0, 80).trim();
        }

        return normalized;
    }
}
