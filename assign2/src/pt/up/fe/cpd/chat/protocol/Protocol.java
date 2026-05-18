package pt.up.fe.cpd.chat.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class Protocol {
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    private Protocol() {
    }

    public static String ok(String message) {
        return OK + " " + message;
    }

    public static String error(String message) {
        return ERROR + " " + message;
    }

    public static String token(String token, Instant expiresAt) {
        return "TOKEN " + token + " " + expiresAt;
    }

    public static String rooms(List<String> roomNames) {
        if (roomNames.isEmpty()) {
            return "ROOMS";
        }

        return "ROOMS " + String.join("|", roomNames);
    }

    public static String roomMessage(String roomName, String username, String message) {
        return "ROOM_MESSAGE " + roomName + " " + username + " " + message;
    }

    public static String botMessage(String roomName, String message) {
        return roomMessage(roomName, "Bot", message);
    }

    public static String systemMessage(String roomName, String message) {
        return "SYSTEM " + roomName + " " + message;
    }

    public static boolean isQuit(String line) {
        if (line == null) {
            return false;
        }

        String normalized = line.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("quit") || normalized.equals("/quit");
    }
}
