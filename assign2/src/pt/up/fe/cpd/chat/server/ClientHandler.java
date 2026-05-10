package pt.up.fe.cpd.chat.server;

import pt.up.fe.cpd.chat.protocol.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(
                     new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true)) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.printf("[%s] %s%n", socket.getRemoteSocketAddress(), line);

                if (Protocol.isQuit(line)) {
                    writer.println(Protocol.ok("bye"));
                    break;
                }

                writer.println(Protocol.ok("received"));
            }
        } catch (IOException exception) {
            System.err.printf("Connection error with %s: %s%n", socket.getRemoteSocketAddress(), exception.getMessage());
        } finally {
            System.out.printf("Closed connection from %s%n", socket.getRemoteSocketAddress());
        }
    }
}
