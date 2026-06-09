package pt.up.fe.cpd.chat.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/* Persistencia de utilizadores em ficheiro de texto (assign2/data/users.txt).
 * Formato por linha: "username;hashSHA256". Os utilizadores sobrevivem a um restart
 * do servidor, as sessoes e salas sao estado volatil em memoria e nao sao persistidas.
 */
public final class UserStore {
    private static final Path DEFAULT_USERS_FILE = Path.of("assign2", "data", "users.txt");

    private final Path usersFile;

    public UserStore() {
        this(DEFAULT_USERS_FILE);
    }

    UserStore(Path usersFile) {
        this.usersFile = usersFile;
    }

    public List<User> loadUsers() {
        try {
            ensureUsersFileExists();
            List<User> users = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    User user = parseUser(line);
                    if (user != null) {
                        users.add(user);
                    }
                }
            }
            return users;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load users from " + usersFile, exception);
        }
    }

    /* APPEND para nao sobrescrever utilizadores ja existentes.
     * Nao e necessario reescrever o ficheiro todo porque nunca removemos utilizadores.
     */
    public void saveUser(User user) {
        if (user.username().contains(";")) {
            throw new IllegalArgumentException("Username must not contain ';'");
        }

        try {
            ensureUsersFileExists();
            try (BufferedWriter writer = Files.newBufferedWriter(
                usersFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND
            )) {
                writer.write(user.username());
                writer.write(';');
                writer.write(user.passwordHash());
                writer.newLine();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save user " + user.username(), exception);
        }
    }

    /* Validacao estrita: ignora linhas mal formadas em vez de crashar.
     * Verifica que existe exatamente um ";" e que nenhum dos campos e vazio.
     */
    private User parseUser(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }

        int separatorIndex = line.indexOf(';');
        if (separatorIndex <= 0 || separatorIndex == line.length() - 1 || line.indexOf(';', separatorIndex + 1) >= 0) {
            return null;
        }

        String username = line.substring(0, separatorIndex);
        String passwordHash = line.substring(separatorIndex + 1);
        if (username.isBlank() || passwordHash.isBlank()) {
            return null;
        }

        return new User(username, passwordHash);
    }

    private void ensureUsersFileExists() throws IOException {
        Path parent = usersFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.notExists(usersFile)) {
            Files.createFile(usersFile);
        }
    }
}
