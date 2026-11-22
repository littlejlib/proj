package littlejlib.proj.daemon;

import littlejlib.proj.prp.PrpUuid;
import littlejlib.proj.track.Item;
import littlejlib.proj.track.Workplan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The report over HTTP on a spare port, against a two-project fixture: page, md, json with jq, a write with and
 * without the token, an answer that lands in the workplan and the PRP's timeline.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.daemon.SmokeReportHttp" -Dexec.classpathScope=test
 */
public class SmokeReportHttp {

    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("smokereport");
        var billing = Files.createDirectories(root.resolve("billing").resolve("prp"));
        var ledger = Files.createDirectories(root.resolve("ledger").resolve("prp"));
        Files.writeString(billing.resolve("proj.json"), "{\"uuid\":\"billinguuidtest\",\"name\":\"billing\"}");
        Files.writeString(ledger.resolve("proj.json"), "{\"uuid\":\"ledgeruuidtest\",\"name\":\"ledger\"}");
        Files.writeString(billing.resolve("000100-export.md"), "- PRP Number: 0001\n- PRP Name: export\n");
        var u1 = PrpUuid.ensure(billing.resolve("000100-export.md"), false).uuid();
        var a = Workplan.add(billing, new Item().type("confirm").title("comma or semicolon?").prp(u1));
        Workplan.add(billing, new Item().type("todo").title("write the exporter").prp(u1).deps(List.of(a.id())).uses(List.of("git:billing")));
        Workplan.add(ledger, new Item().type("todo").title("read the export").refs(List.of("after:billing#2")));

        var port = 18688;
        var http = new ReportHttp(port, 10);
        http.start();
        var c = HttpClient.newHttpClient();
        var base = "http://127.0.0.1:" + port;
        // the first lookup by path teaches the process both names
        var with = "with=" + enc(billing.getParent().toString()) + "," + enc(ledger.getParent().toString());

        var page = get(c, base + "/report?" + with);
        check(page.statusCode() == 200 && page.body().contains("comma or semicolon?") && page.body().contains("<svg"), "page " + page.statusCode());
        var token = Pattern.compile("name=\"token\" value=\"([0-9a-f]+)\"").matcher(page.body());
        check(token.find(), "the page carries the token");
        var md = get(c, base + "/report.md?with=billing,ledger");
        check(md.body().contains("## Waiting on the user (1)") && md.body().contains("# Work plan: billing, ledger"), "md, by names: " + md.body());
        var jq = get(c, base + "/report.json?with=billing&jq=" + enc(".items[] | select(.forUser) | .key"));
        check(jq.body().strip().equals("billing#1"), "json with jq: " + jq.body());
        var follow = get(c, base + "/report.json?with=ledger&jq=" + enc(".scope.followed[]"));
        check(follow.body().strip().equals("billing"), "ledger points at billing, so billing is followed in: " + follow.body());

        var noToken = post(c, base + "/item/billing:1", "action=answer&text=comma&back=/report");
        check(noToken.statusCode() == 403, "a write without the token is refused: " + noToken.statusCode());
        var badHost = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/report.md?with=billing")).header("Origin", "http://evil.example").POST(HttpRequest.BodyPublishers.ofString("token=" + token.group(1))).build(), HttpResponse.BodyHandlers.ofString());
        check(badHost.statusCode() == 403, "a post from another origin is refused: " + badHost.statusCode());

        var ok = post(c, base + "/item/billing:1", "token=" + token.group(1) + "&action=answer&text=" + enc("comma, as the bank wants") + "&back=" + enc("/report?with=billing"));
        check(ok.statusCode() == 303 && ok.headers().firstValue("Location").orElse("").contains("msg="), "answer redirects back with a message: " + ok.statusCode());
        var item = Workplan.current(billing).get(1L);
        check("done".equals(item.status()) && "comma, as the bank wants".equals(item.reply()) && item.source().endsWith("(web)") && "web".equals(item.session()), "answer written: " + item);
        var timeline = Files.readString(billing.resolve("000101.status.jsonl"));
        check(timeline.contains("\"kind\":\"decision\"") && timeline.contains("billing#1"), "decision note on the PRP: " + timeline);
        var ready = get(c, base + "/report.json?with=billing&jq=" + enc(".panels.startNow[]"));
        check(ready.body().strip().equals("billing#2"), "the exporter can start now: " + ready.body());

        var added = post(c, base + "/item", "token=" + token.group(1) + "&project=billing&type=todo&title=" + enc("a new one") + "&after=2&back=/report");
        check(added.statusCode() == 303 && Workplan.current(billing).size() == 3, "new item added");
        var itemJson = get(c, base + "/item/billing:3.json");
        check(itemJson.body().contains("\"waitsFor\":[\"billing#2\"]"), "item json: " + itemJson.body());
        http.stop();
        System.out.println("  pass  report http: page, md, json+jq, follow, token, origin, answer, new item");
        System.out.println("ok");
        System.exit(0);
    }

    static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    static HttpResponse<String> get(HttpClient c, String url) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<String> post(HttpClient c, String url, String body) throws Exception {
        return c.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
