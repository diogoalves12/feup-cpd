package pt.up.fe.cpd.chat.server;

import java.io.PrintWriter;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ClientConnection {
    private final String username;
    private final Socket socket;
    private final PrintWriter writer;
    private final ArrayDeque<String> outgoingMessages = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasMessages = lock.newCondition();
    private boolean active = true;

    public ClientConnection(String username, Socket socket, PrintWriter writer) {
        this.username = username;
        this.socket = socket;
        this.writer = writer;
    }

    public String username() {
        return username;
    }

    public boolean isActive() {
        lock.lock();
        try {
            return active;
        } finally {
            lock.unlock();
        }
    }

    public void send(String message) {
        lock.lock();
        try {
            if (!active) {
                return;
            }

            outgoingMessages.addLast(message);
            hasMessages.signal();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            active = false;
            hasMessages.signalAll();
        } finally {
            lock.unlock();
        }

        writer.close();
        try {
            socket.close();
        } catch (IOException ignored) {
            // Socket may already be closed.
        }
    }

    public void startWriter() {
        Thread.ofVirtual().start(this::runWriterLoop);
    }

    private void runWriterLoop() {
        try {
            while (true) {
                String message;
                lock.lock();
                try {
                    while (outgoingMessages.isEmpty() && active) {
                        hasMessages.await();
                    }

                    if (outgoingMessages.isEmpty() && !active) {
                        return;
                    }

                    message = outgoingMessages.removeFirst();
                } finally {
                    lock.unlock();
                }

                writer.println(message);
                writer.flush();
                if (writer.checkError()) {
                    close();
                    return;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
