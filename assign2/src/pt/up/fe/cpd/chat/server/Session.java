package pt.up.fe.cpd.chat.server;

import java.time.Instant;

/* Sessao de um utilizador autenticado: estado volatil em memoria.
 * Separa-se de User (que e persistido em disco) porque a sessao tem tempo de vida
 * limitado (token com expiracao) e pode ser substituida por uma nova ligacao via RESUME.
 * A currentConnection pode mudar sem o utilizador sair da sala.
 */
public final class Session {
    private final String username;
    private final String token;
    private final Instant expiresAt;
    private String currentRoom;
    private ClientConnection currentConnection;

    public Session(String username, String token, Instant expiresAt) {
        this.username = username;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public String username() {
        return username;
    }

    public String token() {
        return token;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String currentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String roomName) {
        currentRoom = roomName;
    }

    public void clearCurrentRoom() {
        currentRoom = null;
    }

    public ClientConnection currentConnection() {
        return currentConnection;
    }

    public void setCurrentConnection(ClientConnection connection) {
        currentConnection = connection;
    }

    public void clearCurrentConnection() {
        currentConnection = null;
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }
}
