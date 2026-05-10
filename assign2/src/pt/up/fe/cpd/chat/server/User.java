package pt.up.fe.cpd.chat.server;

public final class User {
    private final String username;
    private final String passwordHash;

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }
}
