package pt.up.fe.cpd.chat.server;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

import pt.up.fe.cpd.chat.protocol.Protocol;

public final class ServerState {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, User> usersByUsername = new HashMap<>();
    private final Map<String, Session> sessionsByToken = new HashMap<>();
    private final Map<String, Session> sessionsByUsername = new HashMap<>();
    private final Map<String, Room> roomsByName = new HashMap<>();
    private final UserStore userStore;

    public ServerState() {
        this(new UserStore());
    }

    ServerState(UserStore userStore) {
        this.userStore = userStore;
        loadUsers();
    }

    public boolean registerUser(String username, String passwordHash) {
        lock.lock();
        try {
            if (usersByUsername.containsKey(username)) {
                return false;
            }

            User user = new User(username, passwordHash);
            userStore.saveUser(user);
            usersByUsername.put(username, user);
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
        ClientConnection previousConnection = null;
        lock.lock();
        try {
            Session previousSession = sessionsByUsername.put(session.username(), session);
            if (previousSession != null) {
                sessionsByToken.remove(previousSession.token());
                previousConnection = previousSession.currentConnection();
            }

            sessionsByToken.put(session.token(), session);
        } finally {
            lock.unlock();
        }

        if (previousConnection != null) {
            previousConnection.close();
        }

        return session;
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

    public void attachConnection(Session session, ClientConnection connection) {
        ClientConnection previousConnection;
        lock.lock();
        try {
            previousConnection = session.currentConnection();
            session.setCurrentConnection(connection);
        } finally {
            lock.unlock();
        }

        if (previousConnection != null && previousConnection != connection) {
            previousConnection.close();
        }
    }

    public void detachConnection(Session session, ClientConnection connection) {
        lock.lock();
        try {
            if (session.currentConnection() == connection) {
                session.clearCurrentConnection();
            }
        } finally {
            lock.unlock();
        }
    }

    public List<String> listRoomNames() {
        lock.lock();
        try {
            return new ArrayList<>(new TreeSet<>(roomsByName.keySet()));
        } finally {
            lock.unlock();
        }
    }

    public boolean createRoom(String roomName) {
        lock.lock();
        try {
            if (roomsByName.containsKey(roomName)) {
                return false;
            }

            roomsByName.put(roomName, new Room(roomName));
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean createAiRoom(String roomName, String prompt) {
        lock.lock();
        try {
            if (roomsByName.containsKey(roomName)) {
                return false;
            }

            roomsByName.put(roomName, new Room(roomName, true, prompt));
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean joinRoom(Session session, String roomName) {
        lock.lock();
        try {
            Room newRoom = roomsByName.get(roomName);
            if (newRoom == null) {
                return false;
            }

            String previousRoomName = session.currentRoom();
            if (previousRoomName != null) {
                Room previousRoom = roomsByName.get(previousRoomName);
                if (previousRoom != null) {
                    previousRoom.removeMember(session.username());
                }
            }

            newRoom.addMember(session.username());
            session.setCurrentRoom(roomName);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean leaveRoom(Session session) {
        lock.lock();
        try {
            String currentRoomName = session.currentRoom();
            if (currentRoomName == null) {
                return false;
            }

            Room room = roomsByName.get(currentRoomName);
            if (room != null) {
                room.removeMember(session.username());
            }

            session.clearCurrentRoom();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean broadcastRoomMessage(Session senderSession, String message) {
        List<ClientConnection> targets = new ArrayList<>();
        String roomName;
        String formattedMessage;

        lock.lock();
        try {
            roomName = senderSession.currentRoom();
            if (roomName == null) {
                return false;
            }

            Room room = roomsByName.get(roomName);
            if (room == null) {
                return false;
            }

            formattedMessage = Protocol.roomMessage(roomName, senderSession.username(), message);
            room.addMessage(formattedMessage);
            collectActiveConnectionsLocked(room.memberUsernamesCopy(), targets);
        } finally {
            lock.unlock();
        }

        sendToTargets(targets, formattedMessage);
        return true;
    }

    public boolean isAiRoom(String roomName) {
        lock.lock();
        try {
            Room room = roomsByName.get(roomName);
            return room != null && room.isAiRoom();
        } finally {
            lock.unlock();
        }
    }

    public AiRoomContext aiRoomContext(String roomName) {
        lock.lock();
        try {
            Room room = roomsByName.get(roomName);
            if (room == null || !room.isAiRoom()) {
                return null;
            }

            return new AiRoomContext(room.name(), room.aiPrompt(), room.messageLogCopy());
        } finally {
            lock.unlock();
        }
    }

    public void broadcastBotMessage(String roomName, String botMessage) {
        List<ClientConnection> targets = new ArrayList<>();
        String formattedMessage;

        lock.lock();
        try {
            Room room = roomsByName.get(roomName);
            if (room == null) {
                return;
            }

            formattedMessage = Protocol.botMessage(roomName, botMessage);
            room.addMessage(formattedMessage);
            collectActiveConnectionsLocked(room.memberUsernamesCopy(), targets);
        } finally {
            lock.unlock();
        }

        sendToTargets(targets, formattedMessage);
    }

    public void broadcastSystemMessage(String roomName, String message) {
        List<ClientConnection> targets = new ArrayList<>();
        String formattedMessage;

        lock.lock();
        try {
            Room room = roomsByName.get(roomName);
            if (room == null) {
                return;
            }

            formattedMessage = Protocol.systemMessage(roomName, message);
            room.addMessage(formattedMessage);
            collectActiveConnectionsLocked(room.memberUsernamesCopy(), targets);
        } finally {
            lock.unlock();
        }

        sendToTargets(targets, formattedMessage);
    }

    private void collectActiveConnectionsLocked(List<String> memberUsernames, List<ClientConnection> targets) {
        for (String username : memberUsernames) {
            Session memberSession = sessionsByUsername.get(username);
            if (memberSession == null) {
                continue;
            }

            ClientConnection connection = memberSession.currentConnection();
            if (connection != null && connection.isActive()) {
                targets.add(connection);
            }
        }
    }

    private void sendToTargets(List<ClientConnection> targets, String message) {
        for (ClientConnection connection : targets) {
            connection.send(message);
        }
    }

    private void loadUsers() {
        lock.lock();
        try {
            for (User user : userStore.loadUsers()) {
                usersByUsername.putIfAbsent(user.username(), user);
            }
        } finally {
            lock.unlock();
        }
    }

    public record AiRoomContext(String roomName, String prompt, List<String> messageLog) {
        public AiRoomContext {
            messageLog = List.copyOf(messageLog);
        }
    }
}
