package littlejlib.proj.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Asks the providers of {@link AiConfig} in order; the first that answers wins. Keys are never printed. */
public final class Ai {

    private Ai() { }

    public record Answer(String text, String provider, List<String> failures) { }

    public static Answer ask(String system, String prompt) throws IOException {
        return ask(AiConfig.load(), system, prompt);
    }

    public static Answer ask(AiConfig cfg, String system, String prompt) {
        var failures = new ArrayList<String>();
        for (var p : cfg.provider) {
            if (!p.enabled) continue;
            try {
                var text = "command".equals(p.type) ? command(cfg, p, system, prompt) : anthropic(cfg, p, system, prompt);
                if (text != null && !text.isBlank()) return new Answer(text.strip(), p.name, failures);
                failures.add(p.name + ": empty answer");
            } catch (Exception e) {
                failures.add(p.name + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }
        throw new IllegalStateException("no AI provider answered (" + AiConfig.file() + "): " + String.join("; ", failures));
    }

    static int timeout(AiConfig cfg, AiConfig.Provider p) {
        return p.timeoutSeconds != null ? p.timeoutSeconds : cfg.timeoutSeconds;
    }

    static String anthropic(AiConfig cfg, AiConfig.Provider p, String system, String prompt) throws Exception {
        if (p.url == null || p.url.isBlank()) throw new IllegalArgumentException("no url");
        String key = p.apiKey;
        if ((key == null || key.isBlank()) && p.apiKeyEnv != null && !p.apiKeyEnv.isBlank()) {
            key = System.getenv(p.apiKeyEnv);
            if (key == null || key.isBlank()) throw new IllegalStateException(p.apiKeyEnv + " is not set");
        }
        var body = new JsonObject();
        body.addProperty("model", p.model);
        body.addProperty("max_tokens", cfg.maxTokens);
        if (system != null && !system.isBlank()) body.addProperty("system", system);
        var messages = new JsonArray();
        var m = new JsonObject();
        m.addProperty("role", "user");
        m.addProperty("content", prompt);
        messages.add(m);
        body.add("messages", messages);
        var req = HttpRequest.newBuilder(URI.create(p.url.replaceAll("/+$", "") + "/v1/messages"))
                .timeout(Duration.ofSeconds(timeout(cfg, p)))
                .header("content-type", "application/json")
                .header("anthropic-version", "2023-06-01")
                .header("x-api-key", key == null || key.isBlank() ? "none" : key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + res.statusCode() + " " + cut(res.body()));
        var json = JsonParser.parseString(res.body()).getAsJsonObject();
        var sb = new StringBuilder();
        for (var c : json.getAsJsonArray("content"))
            if ("text".equals(c.getAsJsonObject().get("type").getAsString())) sb.append(c.getAsJsonObject().get("text").getAsString());
        return sb.toString();
    }

    static String command(AiConfig cfg, AiConfig.Provider p, String system, String prompt) throws Exception {
        if (p.command == null || p.command.isEmpty()) throw new IllegalArgumentException("no command");
        var cmd = new ArrayList<>(p.command);
        if (System.getProperty("os.name").startsWith("Windows")) cmd.addAll(0, List.of("cmd", "/c"));
        // Output and errors go to files, so a program that never ends cannot block us past the timeout.
        var outFile = java.nio.file.Files.createTempFile("proj-ai-", ".out");
        var errFile = java.nio.file.Files.createTempFile("proj-ai-", ".err");
        try {
            var proc = new ProcessBuilder(cmd).redirectOutput(outFile.toFile()).redirectError(errFile.toFile()).start();
            try (var in = proc.getOutputStream()) {
                in.write(((system == null || system.isBlank() ? "" : system + "\n\n---\n\n") + prompt).getBytes(StandardCharsets.UTF_8));
            }
            if (!proc.waitFor(timeout(cfg, p), TimeUnit.SECONDS)) {
                proc.descendants().forEach(ProcessHandle::destroyForcibly);
                proc.destroyForcibly();
                throw new IllegalStateException("timed out after " + timeout(cfg, p) + " s");
            }
            if (proc.exitValue() != 0) throw new IllegalStateException("exit " + proc.exitValue() + " " + cut(java.nio.file.Files.readString(errFile)));
            return java.nio.file.Files.readString(outFile, StandardCharsets.UTF_8);
        } finally {
            java.nio.file.Files.deleteIfExists(outFile);
            java.nio.file.Files.deleteIfExists(errFile);
        }
    }

    static String cut(String s) {
        s = s == null ? "" : s.replaceAll("\\s+", " ");
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
