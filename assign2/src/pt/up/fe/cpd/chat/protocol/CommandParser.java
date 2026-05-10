package pt.up.fe.cpd.chat.protocol;

import java.util.List;
import java.util.Locale;

public final class CommandParser {
    private CommandParser() {
    }

    public static ClientCommand parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }

        String trimmed = line.trim();
        String commandName = firstToken(trimmed).toUpperCase(Locale.ROOT);

        return switch (commandName) {
            case "REGISTER" -> parseTwoArgumentCommand(trimmed, CommandType.REGISTER, "REGISTER requires username and password");
            case "LOGIN" -> parseTwoArgumentCommand(trimmed, CommandType.LOGIN, "LOGIN requires username and password");
            case "RESUME" -> parseSingleArgumentCommand(trimmed, CommandType.RESUME, "RESUME requires a token");
            case "LIST_ROOMS" -> parseNoArgumentCommand(trimmed, CommandType.LIST_ROOMS);
            case "JOIN" -> parseSingleArgumentCommand(trimmed, CommandType.JOIN, "JOIN requires a room name");
            case "CREATE_ROOM" -> parseSingleArgumentCommand(trimmed, CommandType.CREATE_ROOM, "CREATE_ROOM requires a room name");
            case "CREATE_AI_ROOM" -> parseCreateAiRoom(trimmed);
            case "MSG" -> parseMessage(trimmed);
            case "LEAVE" -> parseNoArgumentCommand(trimmed, CommandType.LEAVE);
            case "QUIT", "/QUIT" -> parseNoArgumentCommand(trimmed, CommandType.QUIT);
            default -> throw new IllegalArgumentException("Unknown command");
        };
        
    }

    private static ClientCommand parseTwoArgumentCommand(String line, CommandType type, String errorMessage) {
        String[] parts = line.split("\\s+");
        if (parts.length != 3) {
            throw new IllegalArgumentException(errorMessage);
        }

        return new ClientCommand(type, List.of(parts[1], parts[2]));
    }

    private static ClientCommand parseSingleArgumentCommand(String line, CommandType type, String errorMessage) {
        String[] parts = line.split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException(errorMessage);
        }

        return new ClientCommand(type, List.of(parts[1]));
    }

    private static ClientCommand parseNoArgumentCommand(String line, CommandType type) {
        String[] parts = line.split("\\s+");
        if (parts.length != 1) {
            throw new IllegalArgumentException(type + " does not take arguments");
        }

        return new ClientCommand(type, List.of());
    }

    private static ClientCommand parseCreateAiRoom(String line) {
        String remainder = removeCommandPrefix(line);
        int separatorIndex = remainder.indexOf('|');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("CREATE_AI_ROOM requires a room name and prompt separated by |");
        }

        String roomName = remainder.substring(0, separatorIndex).trim();
        String prompt = remainder.substring(separatorIndex + 1).trim();
        if (roomName.isEmpty() || prompt.isEmpty()) {
            throw new IllegalArgumentException("CREATE_AI_ROOM requires a room name and prompt separated by |");
        }

        return new ClientCommand(CommandType.CREATE_AI_ROOM, List.of(roomName, prompt));
    }

    private static ClientCommand parseMessage(String line) {
        String message = removeCommandPrefix(line).trim();
        if (message.isEmpty()) {
            throw new IllegalArgumentException("MSG requires message text");
        }

        return new ClientCommand(CommandType.MSG, List.of(message));
    }

    private static String firstToken(String line) {
        int separatorIndex = line.indexOf(' ');
        if (separatorIndex < 0) {
            return line;
        }

        return line.substring(0, separatorIndex);
    }

    private static String removeCommandPrefix(String line) {
        int separatorIndex = line.indexOf(' ');
        if (separatorIndex < 0) {
            return "";
        }

        return line.substring(separatorIndex + 1);
    }
}
