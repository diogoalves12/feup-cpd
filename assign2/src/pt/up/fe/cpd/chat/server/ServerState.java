package pt.up.fe.cpd.chat.server;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public final class ServerState {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, User> usersByUsername = new HashMap<>();
    private final Map<String, Session> sessionsByToken = new HashMap<>();
    private final Map<String, Session> sessionsByUsername = new HashMap<>();

    public boolean registerUser(String username, String passwordHash) {
        lock.lock();
        try {
            if (usersByUsername.containsKey(username)) {
                return false;
            }

            usersByUsername.put(username, new User(username, passwordHash));
            return true;
        } finally {
            lock.unlock();
        }
    }

    public User findUser(String username) {
        lock.lock();
        try {
            return usersByUsername.get(username);
        } finally {
            lock.unlock();
        }
    }

    public Session storeSession(Session session) {
        lock.lock();
        try {
            Session previousSession = sessionsByUsername.put(session.username(), session);
            if (previousSession != null) {
                sessionsByToken.remove(previousSession.token());
            }

            sessionsByToken.put(session.token(), session);
            return session;
        } finally {
            lock.unlock();
        }
    }

    public Session findValidSession(String token, Instant now) {
        lock.lock();
        try {
            Session session = sessionsByToken.get(token);
            if (session == null) {
                return null;
            }

            if (session.isExpiredAt(now)) {
                sessionsByToken.remove(token);
                sessionsByUsername.remove(session.username(), session);
                return null;
            }

            return session;
        } finally {
            lock.unlock();
        }
    }
}
