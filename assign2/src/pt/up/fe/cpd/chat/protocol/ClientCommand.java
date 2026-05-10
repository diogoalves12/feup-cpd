package pt.up.fe.cpd.chat.protocol;

import java.util.List;

public record ClientCommand(CommandType type, List<String> arguments) {
    
    public ClientCommand {
        arguments = List.copyOf(arguments);
    }
    
}
