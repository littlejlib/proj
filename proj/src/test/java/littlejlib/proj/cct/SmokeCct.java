package littlejlib.proj.cct;

import littlejlib.proj.cct.action.*;
import littlejlib.proj.cct.db.Db;
import littlejlib.proj.cct.export.*;
import littlejlib.proj.cct.search.*;

/**
 * What the transcripts half does, checked against the REAL index rather than fixtures, because every
 * bug worth catching here came from the shape of the actual store: prompts re-emitted three times,
 * PRP files renamed on closing, cmd.exe not changing drive.
 * <p>
 * Run with the daemon STOPPED - it holds the index:
 * {@code mvn exec:java -Dexec.mainClass=littlejlib.proj.cct.SmokeCct -Dexec.classpathScope=test}
 */
public final class SmokeCct {

    public static void main(String[] args) {
        var db = Db.get().db();
        var svc = new SearchService(db);
        var sessions = Sessions.all(db);
        say("sessions indexed", sessions.size());

        var withPrp = 0;
        var resolved = 0;
        var closedVariant = 0;
        for (var s : sessions) {
            var named = PrpLink.mentioned(s.firstHumanPrompt());
            if (named == null) continue;
            withPrp++;
            var file = PrpLink.of(s);
            if (file == null) continue;
            resolved++;
            if (file.toString().contains(".closed.") && !named.contains(".closed.")) closedVariant++;
        }
        say("sessions naming a PRP in their first prompt", withPrp);
        say("  of those, PRP file found on disk", resolved);
        say("  found only via the .closed rename", closedVariant);

        var deduped = 0;
        var checked = 0;
        for (var s : sessions) {
            if (Boolean.TRUE.equals(s.transcriptMissing())) continue;
            var turns = svc.turns(s.sessionId(), 1);
            if (turns.size() < 2) continue;
            checked++;
            for (var i = 1; i < turns.size(); i++) {
                var a = turns.get(i - 1).text();
                var b = turns.get(i).text();
                if (a != null && b != null && !a.isBlank() && b.startsWith(a)) deduped++;
            }
            if (checked >= 200) break;
        }
        say("sessions checked for re-emitted prompts", checked);
        say("  adjacent human turns where one is a prefix of the next (want 0)", deduped);

        var sample = sessions.stream().filter(s -> !Boolean.TRUE.equals(s.transcriptMissing())
            && s.cwd() != null && s.cwd().length() > 3).findFirst().orElse(null);
        if (sample == null) { say("no resumable session to check commands against", 0); return; }

        var cmd = ResumeCommands.in(ShellKind.CMD, sample, false, false);
        var bash = ResumeCommands.in(ShellKind.BASH, sample, false, false);
        check("cmd form uses /d so it changes drive", cmd.startsWith("cd /d \""));
        check("cmd form chains with &", cmd.contains("\" & claude --resume "));
        check("bash form uses a posix path", bash.contains("cd \"/"));
        check("powershell form chains with ;",
            ResumeCommands.in(ShellKind.POWERSHELL, sample, false, false).contains("\" ; claude"));

        var turns = svc.turns(sample.sessionId(), 2);
        var md = MarkdownExport.render(sample, turns);
        var xhtml = XHtmlExport.render(sample, turns);
        check("markdown has a heading", md.startsWith("# "));
        check("markdown never wraps a paragraph", noWrappedParagraph(md));
        check("xhtml has a real title element", xhtml.contains("<title>"));
        check("xhtml has a real style element", xhtml.contains("<style>"));
        check("xhtml carries the turns", xhtml.contains("<human") || xhtml.contains("<assistant"));
        check("text export renders", !TextExport.render(sample, turns).isBlank());
        say("exported turns", turns.size());
    }

    /**
     * The house rule: a paragraph is ONE line however long. A renderer that wrapped would show up as
     * many lines of a similar width, so this looks for the tell - lines between 60 and 100 chars,
     * which is what a wrapper produces and prose almost never does by itself.
     */
    static boolean noWrappedParagraph(String text) {
        var suspicious = 0;
        for (var line : text.split("\n", -1)) {
            var n = line.length();
            if (n >= 60 && n <= 100) suspicious++;
        }
        return suspicious < 20;
    }

    static void say(String what, int n) { System.out.println("  " + n + "\t" + what); }

    static void check(String what, boolean ok) { System.out.println("  " + (ok ? "ok  " : "FAIL") + "\t" + what); }
}
