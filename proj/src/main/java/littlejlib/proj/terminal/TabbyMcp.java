package littlejlib.proj.terminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code tabby-mcp-server} plugin, spoken to over plain REST rather than MCP: every tool is also
 * a {@code POST /api/tool/<name>}, so no handshake and no session are needed.
 * <p>
 * This is here because Tabby's OWN command line cannot run anything - see
 * {@code prp/32-prp.01.findings.md}. Absent plugin means absent, not broken: every method degrades
 * to null/false and the caller falls back.
 */
public final class TabbyMcp {

    private TabbyMcp() { }

    static final int FALLBACK_PORT = 3001;
    static final long RECHECK_MS = 30_000;

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(700)).build();

    static volatile int cachedPort = -1;
    static volatile long cachedAt;

    /** The port it answers on, or -1. Re-probed every half minute so a restarted Tabby is found. */
    public static int port() {
        var now = System.currentTimeMillis();
        if (cachedPort > 0 && now - cachedAt < RECHECK_MS) return cachedPort;
        var found = -1;
        for (var candidate : candidatePorts()) {
            if (healthy(candidate)) { found = candidate; break; }
        }
        cachedPort = found;
        cachedAt = now;
        return found;
    }

    public static boolean available() {
        return port() > 0;
    }

    static List<Integer> candidatePorts() {
        var out = new ArrayList<Integer>();
        var configured = configuredPort();
        if (configured > 0) out.add(configured);
        if (!out.contains(FALLBACK_PORT)) out.add(FALLBACK_PORT);
        return out;
    }

    /** {@code mcp.port} out of Tabby's config.yaml, read as text - one integer is not worth a parser. */
    static int configuredPort() {
        var appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) return -1;
        var file = Path.of(appData, "tabby", "config.yaml");
        try {
            var inMcp = false;
            for (var line : Files.readAllLines(file)) {
                if (!line.startsWith(" ") && !line.isBlank()) inMcp = line.startsWith("mcp:");
                else if (inMcp) {
                    var m = line.strip();
                    if (m.startsWith("port:")) return Integer.parseInt(m.substring(5).strip());
                }
            }
        } catch (Exception e) {
            // no config, no Tabby, or a port that is not a number: all mean "probe the default"
        }
        return -1;
    }

    static boolean healthy(int port) {
        var body = get(port, "/health");
        return body != null && body.contains("Tabby MCP");
    }

    public static List<String> sessionIds() {
        var out = new ArrayList<String>();
        var sessions = call("get_session_list", "{}");
        if (sessions == null || !sessions.isArray()) return out;
        for (var s : sessions) {
            var id = s.path("sessionId").asText(null);
            if (id != null) out.add(id);
        }
        return out;
    }

    /** Types straight into the shell. Not {@code exec_command}, which waits and asks permission. */
    public static boolean sendInput(String sessionId, String input) {
        var reply = call("send_input", JSON.createObjectNode()
                .put("sessionId", sessionId).put("input", input).toString());
        return reply != null && reply.path("success").asBoolean(false);
    }

    public static String buffer(String sessionId, int maxLines) {
        var reply = call("get_terminal_buffer", JSON.createObjectNode()
                .put("sessionId", sessionId).put("maxLines", maxLines).toString());
        return reply == null ? null : reply.path("content").asText(null);
    }

    /**
     * One tool call. The payload arrives as JSON inside a text block inside the MCP envelope, so it
     * is parsed twice - which is the protocol's shape, not an accident.
     */
    static JsonNode call(String tool, String body) {
        var port = port();
        if (port <= 0) return null;
        try {
            var request = HttpRequest.newBuilder(URI.create(base(port) + "/api/tool/" + tool))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var reply = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (reply.statusCode() != 200) return null;
            var text = JSON.readTree(reply.body()).path("content").path(0).path("text").asText(null);
            return text == null ? null : JSON.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    static String get(int port, String path) {
        try {
            var request = HttpRequest.newBuilder(URI.create(base(port) + path))
                    .timeout(Duration.ofMillis(900)).GET().build();
            var reply = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return reply.statusCode() == 200 ? reply.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String base(int port) {
        return "http://127.0.0.1:" + port;
    }
}
