package pt.up.fe.cpd.chat.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/* Sala de chat: guarda membros, historico de mensagens e (se for sala IA) o prompt.
 * Nao tem lock proprio, e protegida pelo lock global do ServerState.
 */
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

    /* Devolve uma copia defensiva dos membros. O caller recebe uma snapshot e pode
     * usa-la depois de libertar o lock sem risco de a colecao ser modificada entretanto.
     */
    public List<String> memberUsernamesCopy() {
        return new ArrayList<>(memberUsernames);
    }

    public void addMessage(String message) {
        messageHistory.add(message);
    }

    // Mesmo principio que memberUsernamesCopy,  snapshot segura para usar fora do lock.
    public List<String> messageLogCopy() {
        return new ArrayList<>(messageHistory);
    }
}
