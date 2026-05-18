package pt.up.fe.cpd.chat.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OllamaClient {
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3:latest";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final URI generateUri;
    private final String model;

    public OllamaClient() {
        this(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
            System.getProperty("chat.ollama.baseUrl", DEFAULT_BASE_URL),
            System.getProperty("chat.ollama.model", DEFAULT_MODEL)
        );
    }

    OllamaClient(HttpClient httpClient, String baseUrl, String model) {
        this.httpClient = httpClient;
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        this.generateUri = URI.create(normalizedBaseUrl + "/api/generate");
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
    }

    public String generate(String prompt) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(generateUri)
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(model, prompt)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }

            String parsedResponse = extractJsonStringField(response.body(), "response");
            if (parsedResponse == null || parsedResponse.isBlank()) {
                throw new IOException("empty response");
            }

            return parsedResponse.trim();
        } catch (IOException exception) {
            throw new IOException(shortReason(exception.getMessage()), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("request interrupted", exception);
        } catch (RuntimeException exception) {
            throw new IOException("invalid response", exception);
        }
    }

    private String buildRequestBody(String modelName, String prompt) {
        return "{"
            + "\"model\":\"" + escapeJson(modelName) + "\","
            + "\"prompt\":\"" + escapeJson(prompt) + "\","
            + "\"stream\":false"
            + "}";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }

        return escaped.toString();
    }

    private static String extractJsonStringField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int fieldIndex = json.indexOf(needle);
        if (fieldIndex < 0) {
            return null;
        }

        int colonIndex = json.indexOf(':', fieldIndex + needle.length());
        if (colonIndex < 0) {
            return null;
        }

        int quoteIndex = json.indexOf('"', colonIndex + 1);
        if (quoteIndex < 0) {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int index = quoteIndex + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaping) {
                value.append(unescapeJsonCharacter(current));
                escaping = false;
                continue;
            }

            if (current == '\\') {
                escaping = true;
                continue;
            }

            if (current == '"') {
                return value.toString();
            }

            value.append(current);
        }

        return null;
    }

    private static char unescapeJsonCharacter(char current) {
        return switch (current) {
            case '"', '\\', '/' -> current;
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            default -> current;
        };
    }

    private static String shortReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "service unavailable";
        }

        String normalized = reason.replace('\n', ' ').trim();
        if (normalized.length() > 48) {
            return normalized.substring(0, 48).trim();
        }

        return normalized;
    }
}
