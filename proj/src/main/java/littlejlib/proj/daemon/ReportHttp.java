package littlejlib.proj.daemon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import littlejlib.proj.report.Feedback;
import littlejlib.proj.report.Jq;
import littlejlib.proj.report.ReportData;
import littlejlib.proj.report.ReportPage;
import littlejlib.proj.report.ReportQuery;
import littlejlib.proj.report.ReportService;
import littlejlib.proj.report.ReportText;
import littlejlib.proj.track.ProjectScope;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * The work plan report over HTTP on 127.0.0.1 (PRP 001Q). Reads are open to anything on this machine; a write needs
 * the token the page carries, which changes every run, and a Host and Origin of this address, so another web page
 * in the browser cannot post to it.
 * <ul>
 * <li>{@code GET /report}, {@code /report.md}, {@code /report.json} ({@code jq=} filters), {@code /report.jsonl}
 * <li>{@code GET /item/Project:id.json}, {@code .md}
 * <li>{@code GET /projects}
 * <li>{@code POST /item/Project:id} (action, text), {@code POST /item} (a new item)
 * </ul>
 */
public final class ReportHttp {

    final ReportService service;
    final int port;
    final String token = HexFormat.of().formatHex(SecureRandom.getSeed(16));
    HttpServer server;

    public ReportHttp(int port, int cacheMinutes) {
        this.port = port;
        this.service = new ReportService(cacheMinutes);
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 16);
        server.setExecutor(Executors.newFixedThreadPool(2, r -> {
            var t = new Thread(r, "proj-report-http");
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/", this::handle);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    void handle(HttpExchange x) throws IOException {
        Server.touched();
        try (x) {
            if (!localHost(x.getRequestHeaders().getFirst("Host"))) {
                send(x, 403, "text/plain", "only http://127.0.0.1:" + port + " or http://localhost:" + port);
                return;
            }
            var path = x.getRequestURI().getRawPath();
            var params = parse(x.getRequestURI().getRawQuery());
            if (x.getRequestMethod().equals("POST")) {
                post(x, path);
                return;
            }
            switch (path) {
                case "/", "/report", "/report.html" -> html(x, params);
                case "/report.md" -> {
                    var q = ReportQuery.of(params);
                    send(x, 200, "text/markdown; charset=utf-8", ReportText.markdown(report(q), q.panels()));
                }
                case "/report.json" -> json(x, report(ReportQuery.of(params)), params.get("jq"), params.containsKey("pretty"));
                case "/report.jsonl" -> send(x, 200, "application/x-ndjson; charset=utf-8", ReportText.jsonl(report(ReportQuery.of(params))));
                case "/projects" -> projects(x);
                default -> {
                    if (path.startsWith("/item/")) item(x, URLDecoder.decode(path.substring(6), StandardCharsets.UTF_8));
                    else send(x, 404, "text/plain", "no such page: " + path);
                }
            }
        } catch (IllegalArgumentException e) {
            send(x, 400, "text/plain; charset=utf-8", e.getMessage());
        } catch (Exception e) {
            Server.log("report: " + e);
            send(x, 500, "text/plain; charset=utf-8", String.valueOf(e));
        }
    }

    ReportData report(ReportQuery q) throws IOException {
        if (q.with().isEmpty()) throw new IllegalArgumentException("name the projects: ?with=<name, alias, uuid or path>[,...]  (GET /projects lists them)");
        return service.report(q);
    }

    void html(HttpExchange x, Map<String, String> params) throws IOException {
        var q = ReportQuery.of(params);
        if (q.with().isEmpty()) {
            send(x, 200, "text/html; charset=utf-8", chooser());
            return;
        }
        var page = ReportPage.render(service.report(q), q, token);
        var msg = params.get("msg");
        if (msg != null) page = page.replaceFirst("<body>", "<body><div class=\"flash\" role=\"status\">" + escape(msg) + "</div>");
        send(x, 200, "text/html; charset=utf-8", page);
    }

    String chooser() throws IOException {
        var sb = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Work plans</title></head><body style=\"font:15px system-ui;margin:20px\"><h1>Work plans</h1><ul>");
        var all = new StringBuilder();
        for (var p : ProjectScope.known()) {
            if (!p.hasWorkplan()) continue;
            sb.append("<li><a href=\"/report?with=").append(escape(p.name())).append("\">").append(escape(p.name())).append("</a> ").append(escape(p.dir().toString())).append("</li>");
            all.append(all.isEmpty() ? "" : ",").append(p.name());
        }
        sb.append("</ul><p><a href=\"/report?with=").append(escape(all.toString())).append("\">All of them together</a></p></body></html>");
        return sb.toString();
    }

    void json(HttpExchange x, ReportData d, String jq, boolean pretty) throws IOException {
        if (jq != null && !jq.isBlank()) {
            send(x, 200, "text/plain; charset=utf-8", String.join("\n", Jq.run(ReportText.json(d, false), jq)) + "\n");
            return;
        }
        send(x, 200, "application/json; charset=utf-8", ReportText.json(d, pretty));
    }

    void projects(HttpExchange x) throws IOException {
        var sb = new StringBuilder("[");
        for (var p : ProjectScope.known()) {
            if (sb.length() > 1) sb.append(',');
            sb.append("{\"name\":").append(q(p.name())).append(",\"uuid\":").append(q(p.uuid())).append(",\"dir\":").append(q(p.dir().toString()))
                    .append(",\"hasWorkplan\":").append(p.hasWorkplan()).append('}');
        }
        send(x, 200, "application/json; charset=utf-8", sb.append("]").toString());
    }

    void item(HttpExchange x, String ref) throws IOException {
        var format = ref.endsWith(".json") ? "json" : ref.endsWith(".md") ? "md" : "html";
        var bare = format.equals("html") ? ref : ref.substring(0, ref.lastIndexOf('.'));
        var parts = ReportQuery.itemRef(bare);
        if (parts == null) throw new IllegalArgumentException("item is Project:id");
        var params = new LinkedHashMap<String, String>();
        params.put("with", parts[0]);
        params.put("done", "1");
        var d = service.report(ReportQuery.of(params));
        var key = d.projects.get(0).name + "#" + parts[1];
        var it = d.byKey.get(key);
        if (it == null) {
            send(x, 404, "text/plain", "no item " + key);
            return;
        }
        switch (format) {
            case "json" -> send(x, 200, "application/json; charset=utf-8", ReportText.json(itemOnly(it), false));
            case "md" -> send(x, 200, "text/markdown; charset=utf-8", "- " + ReportText.line(it, key) + "\n"
                    + (it.detail == null ? "" : "\n" + it.detail + "\n") + "\nsource " + it.source + ", updated " + it.updated + "\n");
            default -> redirect(x, "/report?with=" + parts[0] + "&done=1#" + ReportPage.domId(key));
        }
    }

    static ReportData itemOnly(ReportData.Item it) {
        var d = new ReportData();
        d.items.add(it);
        return d;
    }

    void post(HttpExchange x, String path) throws IOException {
        var origin = x.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.equals("http://127.0.0.1:" + port) && !origin.equals("http://localhost:" + port)) {
            send(x, 403, "text/plain", "writes only from this page");
            return;
        }
        var form = parse(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (!token.equals(form.get("token"))) {
            send(x, 403, "text/plain", "the page is from an earlier run of proj: reload it (r)");
            return;
        }
        var back = form.getOrDefault("back", "/report");
        if (!back.startsWith("/report")) back = "/report";
        String msg;
        try {
            if (path.equals("/item")) msg = Feedback.add(form);
            else if (path.startsWith("/item/")) msg = Feedback.apply(URLDecoder.decode(path.substring(6), StandardCharsets.UTF_8), form);
            else throw new IllegalArgumentException("nothing to post to at " + path);
            var ref = path.startsWith("/item/") ? ReportQuery.itemRef(URLDecoder.decode(path.substring(6), StandardCharsets.UTF_8)) : null;
            if (ref != null) service.invalidate(ProjectScope.resolve(ref[0]).dir());
        } catch (IllegalArgumentException e) {
            msg = "Not done: " + e.getMessage();
        }
        var hash = back.indexOf('#');
        var base = hash < 0 ? back : back.substring(0, hash);
        var frag = hash < 0 ? "" : back.substring(hash);
        redirect(x, base + (base.contains("?") ? "&" : "?") + "msg=" + java.net.URLEncoder.encode(msg, StandardCharsets.UTF_8) + frag);
    }

    boolean localHost(String host) {
        return host != null && (host.equals("127.0.0.1:" + port) || host.equals("localhost:" + port));
    }

    static Map<String, String> parse(String raw) {
        var out = new LinkedHashMap<String, String>();
        if (raw == null || raw.isEmpty()) return out;
        for (var pair : raw.split("&")) {
            var eq = pair.indexOf('=');
            var k = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            var v = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            out.put(k, v);
        }
        return out;
    }

    static void redirect(HttpExchange x, String to) throws IOException {
        x.getResponseHeaders().set("Location", to);
        x.sendResponseHeaders(303, -1);
    }

    static void send(HttpExchange x, int status, String type, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", type);
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
    }

    static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String q(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
