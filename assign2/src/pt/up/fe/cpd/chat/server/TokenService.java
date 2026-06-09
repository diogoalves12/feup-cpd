package pt.up.fe.cpd.chat.server;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/* Cria tokens de sessao UUID com expiracao.
 * O token e equivalente a um cookie HTTP: o cliente guarda-o e apresenta-o
 * no RESUME para retomar a sessao sem precisar de enviar a palavra-passe de novo.
 */
public final class TokenService {
    private static final Duration TOKEN_DURATION = Duration.ofMinutes(30);

    private TokenService() {
    }

    public static Session newSession(String username) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(TOKEN_DURATION);
        return new Session(username, token, expiresAt);
    }
}
