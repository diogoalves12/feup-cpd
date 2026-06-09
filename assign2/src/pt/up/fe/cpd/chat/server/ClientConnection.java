package pt.up.fe.cpd.chat.server;

import java.io.PrintWriter;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/* Gere a fila de saida de mensagens de um cliente ligado.
 * Resolve o problema do slow client: o servidor nunca escreve diretamente no socket 
 * adiciona a uma fila ArrayDeque e uma virtual thread escritora consome ao ritmo do cliente.
 * Se a fila encher (MAX_PENDING_MESSAGES), o cliente e desligado.
 */
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

    /* Adiciona a mensagem a fila e acorda o writer loop.
     * Se a fila estiver cheia (cliente demasiado lento), limpa a fila, envia erro
     * e agenda o fecho do socket com delay para o cliente ter tempo de ler o erro.
     */
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

    // Pequeno delay antes de fechar para dar ao writer loop tempo de enviar a mensagem de erro.
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

    // Virtual thread porque passa a maior parte do tempo bloqueada na escrita do socket (I/O-bound).
    public void startWriter() {
        Thread.ofVirtual().start(this::runWriterLoop);
    }

    /* Loop do escritor: usa Condition para evitar busy-waiting.
     * A thread suspende em await() quando a fila esta vazia e e acordada por signal()
     * quando chega uma mensagem ou quando a conexao fecha.
     */
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
