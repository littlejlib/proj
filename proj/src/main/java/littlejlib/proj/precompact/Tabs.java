package littlejlib.proj.precompact;

import module java.base;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** {@code fxterm-tabs ctl}, run through the exe itself: its .cmd stub cannot be started without cmd.exe re-quoting every argument. */
public final class Tabs {

    private Tabs() { }

    static final Pattern EXE_IN_STUB = Pattern.compile("\"([^\"]+\\.exe)\"", Pattern.CASE_INSENSITIVE);

    static String exe() {
        for (var dir : System.getenv("PATH").split(File.pathSeparator)) {
            var exe = Path.of(dir, "fxterm-tabs.exe");
            if (Files.isRegularFile(exe)) return exe.toString();
            var stub = Path.of(dir, "fxterm-tabs.cmd");
            if (!Files.isRegularFile(stub)) continue;
            try {
                var m = EXE_IN_STUB.matcher(Files.readString(stub));
                if (m.find()) return m.group(1);
            } catch (IOException ignored) { }
        }
        return "fxterm-tabs";
    }

    /** @return the reply, or null when Tabs is not running or refused */
    static JsonObject ctl(String... args) {
        var argv = new ArrayList<>(List.of(exe(), "ctl"));
        argv.addAll(List.of(args));
        try {
            var p = new ProcessBuilder(argv).redirectErrorStream(true).start();
            var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); return null; }
            var reply = JsonParser.parseString(out.strip()).getAsJsonObject();
            return reply.has("ok") && reply.get("ok").getAsBoolean() ? reply : null;
        } catch (IOException | InterruptedException | RuntimeException e) {
            return null;
        }
    }

    public static Optional<TabInfo> byClaudeSession(String sessionId) {
        var reply = ctl("list");
        if (reply == null) return Optional.empty();
        for (var w : reply.getAsJsonArray("windows"))
            for (var t : w.getAsJsonObject().getAsJsonArray("tabs")) {
                var o = t.getAsJsonObject();
                if (!o.has("claude") || !sessionId.equals(str(o.getAsJsonObject("claude"), "session"))) continue;
                var c = o.getAsJsonObject("claude");
                return Optional.of(new TabInfo().id(str(o, "id")).title(str(o, "title")).folder(str(o, "folder"))
                        .live(o.has("live") && o.get("live").getAsBoolean())
                        .claudeSession(sessionId).claudeStatus(str(c, "status")).claudeName(str(c, "name")));
            }
        return Optional.empty();
    }

    public static boolean send(String tabId, String text) {
        return ctl("send", "id=" + tabId, "text=" + text, "enter=true") != null;
    }

    public static boolean hibernate(String tabId) {
        return ctl("hibernate", "id=" + tabId) != null;
    }

    static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
