package pt.up.fe.cpd.chat.client;

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
import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ChatClient {
    private static final int RECONNECT_DELAY_MILLIS = 1_000;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;

    private final String host;
    private final int port;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasOutgoingCommands = lock.newCondition();
    private final ArrayDeque<String> outgoingCommands = new ArrayDeque<>();
    private boolean running = true;
    private boolean consoleClosed;
    private boolean quitRequested;
    private String token;
    private Instant tokenExpiresAt;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            Thread.ofVirtual().start(() -> readConsole(consoleReader));
            runConnectionManager();
        } finally {
            stopClient();
        }
    }

    private void runConnectionManager() throws IOException {
        try {
            Socket initialSocket = connectSocket();
            printHelp();
            runConnectedSession(initialSocket, false);
        } catch (IOException exception) {
            throw new IOException("Unable to connect to the server", exception);
        }

        while (isRunning()) {
            String resumeToken = currentToken();
            if (resumeToken == null) {
                System.err.println("Connection closed and no token is available to resume the session.");
                return;
            }

            if (!attemptReconnectLoop(resumeToken)) {
                return;
            }
        }
    }

    private boolean attemptReconnectLoop(String resumeToken) {
        System.err.printf("Connection lost. Trying to reconnect to %s:%d...%n", host, port);

        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS && isRunning(); attempt++) {
            try {
                Thread.sleep(RECONNECT_DELAY_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }

            try {
                runConnectedSession(connectSocket(), true, resumeToken);
                return true;
            } catch (IOException exception) {
                System.err.printf("Reconnect attempt %d/%d failed: %s%n", attempt, MAX_RECONNECT_ATTEMPTS, exception.getMessage());
            }
        }

        System.err.println("Reconnect attempts exhausted. Exiting client.");
        return false;
    }

    private Socket connectSocket() throws IOException {
        Socket socket = new Socket(host, port);
        System.out.printf("Connected to %s:%d%n", host, port);
        return socket;
    }

    private boolean runConnectedSession(Socket socket, boolean resumeRequested) throws IOException {
        return runConnectedSession(socket, resumeRequested, currentToken());
    }

    private boolean runConnectedSession(Socket socket, boolean resumeRequested, String resumeToken)
        throws IOException {
        try (socket;
             BufferedReader serverReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true)) {

            if (resumeRequested) {
                boolean resumeAccepted = performResumeHandshake(serverReader, serverWriter, resumeToken);
                if (!resumeAccepted) {
                    clearToken();
                    System.err.println("Could not resume previous session. Please login again.");
                }
            }

            ConnectionState connectionState = new ConnectionState();
            Thread readerThread = Thread.ofVirtual().start(() -> readServerReplies(serverReader, connectionState));

            runSenderLoop(socket, serverWriter, connectionState);
            waitForReader(readerThread, connectionState);
            return true;
        }
    }

    private boolean performResumeHandshake(BufferedReader serverReader, PrintWriter serverWriter, String resumeToken)
        throws IOException {
        serverWriter.println("RESUME " + resumeToken);
        serverWriter.flush();

        while (true) {
            String reply = serverReader.readLine();
            if (reply == null) {
                throw new IOException("Connection closed during RESUME");
            }

            printServerReply(reply);
            if (reply.startsWith("OK RESUME")) {
                return true;
            }

            if (reply.startsWith("ERROR Invalid or expired token")) {
                return false;
            }

            if (reply.startsWith("TOKEN ")) {
                captureToken(reply);
            }
        }
    }

    private void runSenderLoop(Socket socket, PrintWriter serverWriter, ConnectionState connectionState) throws IOException {
        while (isRunning()) {
            String command = awaitNextCommand(connectionState);
            if (command == null) {
                if (!connectionState.isAlive()) {
                    return;
                }

                stopClient();
                socket.close();
                return;
            }

            serverWriter.println(command);
            serverWriter.flush();
            if (serverWriter.checkError()) {
                connectionState.markDisconnected();
                socket.close();
                return;
            }

            if (Protocol.isQuit(command)) {
                markQuitRequested();
                try {
                    socket.shutdownOutput();
                } catch (IOException ignored) {
                    socket.close();
                }
                return;
            }
        }
    }

    private String awaitNextCommand(ConnectionState connectionState) {
        lock.lock();
        try {
            while (outgoingCommands.isEmpty() && running && connectionState.isAlive() && !consoleClosed) {
                hasOutgoingCommands.await();
            }

            if (!running || !connectionState.isAlive()) {
                return null;
            }

            if (outgoingCommands.isEmpty() && consoleClosed) {
                return null;
            }

            return outgoingCommands.removeFirst();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void waitForReader(Thread readerThread, ConnectionState connectionState) throws IOException {
        connectionState.markDisconnected();
        wakeSender();

        try {
            readerThread.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the server reader", exception);
        }
    }

    private void readServerReplies(BufferedReader serverReader, ConnectionState connectionState) {
        try {
            String reply;
            while ((reply = serverReader.readLine()) != null) {
                if (reply.startsWith("TOKEN ")) {
                    captureToken(reply);
                }

                printServerReply(reply);
            }
        } catch (IOException exception) {
            if (isRunning() && !quitRequested()) {
                System.err.printf("Server connection error: %s%n", exception.getMessage());
            }
        } finally {
            connectionState.markDisconnected();
            wakeSender();
        }
    }

    private void readConsole(BufferedReader consoleReader) {
        try {
            String line;
            while (isRunning() && (line = consoleReader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                if (isLocalHelpCommand(line)) {
                    printHelp();
                    continue;
                }

                enqueueCommand(line);
                if (Protocol.isQuit(line)) {
                    return;
                }
            }

            markConsoleClosed();
        } catch (IOException exception) {
            if (isRunning()) {
                System.err.printf("Console input error: %s%n", exception.getMessage());
            }
        }
    }

    private boolean isLocalHelpCommand(String line) {
        String command = line.trim();
        return command.equalsIgnoreCase("HELP") || command.equalsIgnoreCase("/help");
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("REGISTER <username> <password>");
        System.out.println("LOGIN <username> <password>");
        System.out.println("LIST_ROOMS");
        System.out.println("CREATE_ROOM <room>");
        System.out.println("CREATE_AI_ROOM <room> | <prompt>");
        System.out.println("JOIN <room>");
        System.out.println("LEAVE");
        System.out.println("MSG <message>");
        System.out.println("or type directly after joining a room");
        System.out.println("HELP");
        System.out.println("QUIT");
        System.out.println();
    }

    private void enqueueCommand(String line) {
        lock.lock();
        try {
            if (!running) {
                return;
            }

            outgoingCommands.addLast(line);
            hasOutgoingCommands.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void printServerReply(String reply) {
        if (reply.startsWith("TOKEN ")) {
            System.out.println("[system] Session started.");
            return;
        }

        if (reply.startsWith("ERROR ")) {
            String[] parts = reply.split("\\s+", 2);
            System.out.printf("[error] %s%n", parts.length == 2 ? parts[1] : "");
            return;
        }

        switch (reply) {
            case "OK REGISTER" -> System.out.println("[system] User registered successfully.");
            case "OK LOGIN" -> System.out.println("[system] Logged in successfully.");
            case "OK RESUME" -> System.out.println("[system] Session resumed.");
            case "OK CREATE_ROOM" -> System.out.println("[system] Room created successfully.");
            case "OK CREATE_AI_ROOM" -> System.out.println("[system] AI room created successfully.");
            case "OK JOIN" -> System.out.println("[system] Joined room.");
            case "OK LEAVE" -> System.out.println("[system] Left room.");
            case "OK bye" -> System.out.println("[system] Goodbye.");
            default -> printStructuredServerReply(reply);
        }
    }

    private void printStructuredServerReply(String reply) {
        if (reply.equals("ROOMS") || reply.equals("ROOMS ")) {
            System.out.println("[system] No rooms available.");
            return;
        }

        if (reply.startsWith("ROOMS ")) {
            printRooms(reply.substring("ROOMS ".length()));
            return;
        }

        if (reply.startsWith("ROOM_MESSAGE ")) {
            String[] parts = reply.split("\\s+", 4);
            if (parts.length == 4) {
                System.out.printf("[%s] %s: %s%n", parts[1], parts[2], parts[3]);
                return;
            }
        }

        if (reply.startsWith("SYSTEM ")) {
            String[] parts = reply.split("\\s+", 3);
            if (parts.length == 3) {
                System.out.printf("[%s] System: %s%n", parts[1], parts[2]);
                return;
            }
        }

        System.out.printf("server> %s%n", reply);
    }

    private void printRooms(String roomsPayload) {
        if (roomsPayload.isBlank()) {
            System.out.println("[system] No rooms available.");
            return;
        }

        System.out.println("Rooms:");
        for (String room : roomsPayload.split("\\|")) {
            if (!room.isBlank()) {
                System.out.printf("- %s%n", room);
            }
        }
    }

    private void captureToken(String reply) {
        String[] parts = reply.split("\\s+", 3);
        if (parts.length != 3) {
            return;
        }

        Instant expiresAt;
        try {
            expiresAt = Instant.parse(parts[2]);
        } catch (RuntimeException exception) {
            return;
        }

        lock.lock();
        try {
            token = parts[1];
            tokenExpiresAt = expiresAt;
        } finally {
            lock.unlock();
        }
    }

    private String currentToken() {
        lock.lock();
        try {
            if (token == null || tokenExpiresAt == null || !tokenExpiresAt.isAfter(Instant.now())) {
                return null;
            }

            return token;
        } finally {
            lock.unlock();
        }
    }

    private void clearToken() {
        lock.lock();
        try {
            token = null;
            tokenExpiresAt = null;
        } finally {
            lock.unlock();
        }
    }

    private boolean isRunning() {
        lock.lock();
        try {
            return running;
        } finally {
            lock.unlock();
        }
    }

    private void markQuitRequested() {
        lock.lock();
        try {
            quitRequested = true;
            running = false;
            hasOutgoingCommands.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean quitRequested() {
        lock.lock();
        try {
            return quitRequested;
        } finally {
            lock.unlock();
        }
    }

    private void stopClient() {
        lock.lock();
        try {
            running = false;
            hasOutgoingCommands.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void wakeSender() {
        lock.lock();
        try {
            hasOutgoingCommands.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void markConsoleClosed() {
        lock.lock();
        try {
            consoleClosed = true;
            hasOutgoingCommands.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) {
        final ChatClient client;
        try {
            client = createFromArgs(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(exception.getMessage());
            return;
        }

        try {
            client.start();
        } catch (IOException exception) {
            System.err.printf("Client error: %s%n", exception.getMessage());
        }
    }

    private static ChatClient createFromArgs(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: ChatClient <host> <port>");
        }

        try {
            int port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            return new ChatClient(args[0], port);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Usage: ChatClient <host> <port>");
        }
    }

    private static final class ConnectionState {
        private final ReentrantLock lock = new ReentrantLock();
        private boolean alive = true;

        boolean isAlive() {
            lock.lock();
            try {
                return alive;
            } finally {
                lock.unlock();
            }
        }

        void markDisconnected() {
            lock.lock();
            try {
                alive = false;
            } finally {
                lock.unlock();
            }
        }
    }
}
