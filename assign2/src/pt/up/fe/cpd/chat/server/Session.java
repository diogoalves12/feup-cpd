package pt.up.fe.cpd.chat.server;

import java.time.Instant;

public final class Session {
    private final String username;
    private final String token;
    private final Instant expiresAt;

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

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }
}
