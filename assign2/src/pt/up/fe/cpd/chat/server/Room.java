package pt.up.fe.cpd.chat.server;

import java.util.HashSet;
import java.util.Set;

public final class Room {
    private final String name;
    private final Set<String> memberUsernames = new HashSet<>();

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
}
