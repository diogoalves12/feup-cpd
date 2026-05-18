package pt.up.fe.cpd.chat.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Room {
    private final String name;
    private final boolean aiRoom;
    private final String aiPrompt;
    private final Set<String> memberUsernames = new HashSet<>();
    private final List<String> messageHistory = new ArrayList<>();

    public Room(String name) {
        this(name, false, null);
    }

    public Room(String name, boolean aiRoom, String aiPrompt) {
        this.name = name;
        this.aiRoom = aiRoom;
        this.aiPrompt = aiPrompt;
    }

    public String name() {
        return name;
    }

    public boolean isAiRoom() {
        return aiRoom;
    }

    public String aiPrompt() {
        return aiPrompt;
    }

    public void addMember(String username) {
        memberUsernames.add(username);
    }

    public void removeMember(String username) {
        memberUsernames.remove(username);
    }

    public List<String> memberUsernamesCopy() {
        return new ArrayList<>(memberUsernames);
    }

    public void addMessage(String message) {
        messageHistory.add(message);
    }

    public List<String> messageLogCopy() {
        return new ArrayList<>(messageHistory);
    }
}
