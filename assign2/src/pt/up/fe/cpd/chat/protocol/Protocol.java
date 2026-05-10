package pt.up.fe.cpd.chat.protocol;

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

    public static boolean isQuit(String line) {
        if (line == null) {
            return false;
        }

        String normalized = line.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("quit") || normalized.equals("/quit");
    }
}
