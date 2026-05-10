package pt.up.fe.cpd.chat.protocol;

import java.util.Locale;

public final class Protocol {
    public static final String OK = "OK";

    private Protocol() {
    }

    public static String ok(String message) {
        return OK + " " + message;
    }

    public static boolean isQuit(String line) {
        if (line == null) {
            return false;
        }

        String normalized = line.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("quit") || normalized.equals("/quit");
    }
}
