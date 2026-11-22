package littlejlib.proj.migrate;

import module java.base;

import com.fasterxml.jackson.databind.ObjectMapper;

/** The running fxterm-tabs through its own control interface ({@code fxterm-tabs ctl ...}); its files are never touched. */
public final class TabsCtl {

    private TabsCtl() { }

    static final ObjectMapper JSON = new ObjectMapper();
    static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.-]+");

    /** The tabs of every window (group labels left out); null when Tabs is not running or does not answer. */
    public static List<TabInfo> list() {
        try {
            return parse(run("list"));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static List<TabInfo> parse(String json) throws IOException {
        var root = JSON.readTree(json);
        if (!root.path("ok").asBoolean(false)) throw new IOException(root.path("error").asText("not ok"));
        var out = new ArrayList<TabInfo>();
        for (var w : root.path("windows"))
            for (var t : w.path("tabs")) {
                if (t.path("separator").asBoolean(false)) continue;
                var claude = t.path("claude");
                out.add(new TabInfo().id(t.path("id").asText()).title(t.path("title").asText(null))
                        .folder(t.path("folder").asText(null)).live(t.path("live").asBoolean(false))
                        .session(claude.isMissingNode() ? null : claude.path("session").asText(null)));
            }
        return out;
    }

    /** Renames a tab; a title with characters a command line could mangle is refused rather than passed on. */
    public static boolean rename(String id, String title) {
        if (!SAFE.matcher(id).matches() || !SAFE.matcher(title).matches()) return false;
        try {
            return JSON.readTree(run("rename", "id=" + id, "title=" + title)).path("ok").asBoolean(false);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static String run(String... ctl) throws IOException {
        var cmd = new ArrayList<String>();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) cmd.addAll(List.of("cmd", "/c"));
        cmd.addAll(List.of("fxterm-tabs", "ctl"));
        cmd.addAll(List.of(ctl));
        var p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectInput(ProcessBuilder.Redirect.from(nullFile())).start();
        var out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            if (!p.waitFor(20, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    static File nullFile() {
        return new File(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null");
    }
}
