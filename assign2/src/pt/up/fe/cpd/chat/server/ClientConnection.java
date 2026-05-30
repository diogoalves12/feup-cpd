package pt.up.fe.cpd.chat.server;

import java.io.PrintWriter;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ClientConnection {
    private static final int MAX_PENDING_MESSAGES = 100;
    private static final String QUEUE_FULL_MESSAGE = "ERROR Client too slow: disconnecting";
    private static final long CLOSE_TIMEOUT_MS = 200;

    private final String username;
    private final Socket socket;
    private final PrintWriter writer;
    private final ArrayDeque<String> outgoingMessages = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasMessages = lock.newCondition();
    private boolean active = true;
    private boolean closed;

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
        boolean shouldCloseAfterTimeout = false;

        lock.lock();
        try {
            if (!active) {
                return;
            }

            if (outgoingMessages.size() >= MAX_PENDING_MESSAGES) {
                outgoingMessages.clear();
                outgoingMessages.addLast(QUEUE_FULL_MESSAGE);
                active = false;
                hasMessages.signalAll();
                shouldCloseAfterTimeout = true;

            } else {
                outgoingMessages.addLast(message);
                hasMessages.signal();
            }
            
        } finally {
            lock.unlock();
        }

        if (shouldCloseAfterTimeout) {
            Thread.ofVirtual().start(this::closeClientAfterTimeout);
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

        closeClient();
    }

    private void closeClientAfterTimeout() {
        try {
            Thread.sleep(CLOSE_TIMEOUT_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        closeClient();
    }

    private void closeClient() {
        lock.lock();
        try {
            if (closed) {
                return;
            }

            closed = true;
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
        } finally {
            closeClient();
        }
    }
}
