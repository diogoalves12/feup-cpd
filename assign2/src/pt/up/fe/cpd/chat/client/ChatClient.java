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

public final class ChatClient {
    private final String host;
    private final int port;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader serverReader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverWriter = new PrintWriter(
                     new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
            BufferedReader consoleReader = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.printf("Connected to %s:%d%n", host, port);
            Thread replyReader = Thread.ofVirtual().start(() -> readReplies(serverReader));

            String line;
            while ((line = consoleReader.readLine()) != null) {
                serverWriter.println(line);
                if (Protocol.isQuit(line)) {
                    break;
                }
            }

            socket.shutdownOutput();
            replyReader.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the reply reader", exception);
        }
    }

    private void readReplies(BufferedReader serverReader) {
        try {
            String reply;
            while ((reply = serverReader.readLine()) != null) {
                System.out.printf("server> %s%n", reply);
            }
        } catch (IOException exception) {
            System.err.printf("Server connection error: %s%n", exception.getMessage());
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
}
