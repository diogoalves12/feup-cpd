package pt.up.fe.cpd.chat.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Room {
    private final String name;
    private final Set<String> memberUsernames = new HashSet<>();
    private final List<String> messageHistory = new ArrayList<>();

    public Room(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void addMember(String username) {
        memberUsernames.add(username);
    }

    public void removeMember(String username) {
        memberUsernames.remove(username);
    }

    public List<String> memberUsernamesSnapshot() {
        return new ArrayList<>(memberUsernames);
    }

    public void addMessage(String message) {
        messageHistory.add(message);
    }
}
