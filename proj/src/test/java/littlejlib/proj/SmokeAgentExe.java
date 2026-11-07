package littlejlib.proj;

import littlejlib.proj.prp.AgentExe;
import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.prp.PrpPrompt;
import littlejlib.proj.prp.PrpResume;
import littlejlib.proj.prp.PrpSession;

import java.nio.file.Files;

/**
 * PRP 52: --exe replaces the program the agent runs, in launch, resume, hib restore and cct resume, and a
 * recorded non-default exe is asked about, never assumed. Exits 1 on any miss.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokeAgentExe" -Dexec.classpathScope=test
 */
public class SmokeAgentExe {

    static int failures;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("smoke-agent-exe");
        var prpDir = Files.createDirectory(dir.resolve("prp"));
        var prp = Files.writeString(prpDir.resolve("01-prp-demo.md"), "# demo\n");
        var settings = new Settings();
        settings.setAgentSkipPermissions(true);

        var plain = AgentLauncher.planWith(prp, settings, null, null);
        var free = AgentLauncher.planWith(prp, settings, "haiku", "fclaude");
        System.out.println("plain  " + plain.command());
        System.out.println("free   " + free.command());
        check("no exe keeps claude", plain.command().startsWith("claude ") && plain.exe() == null);
        check("--exe fclaude replaces the program", free.command().startsWith("fclaude --dangerously-skip-permissions --model haiku "));
        check("the plan remembers the exe", "fclaude".equals(free.exe()));
        var withExe = AgentLauncher.plan(prp, settings, "sid", "tab", "haiku", "fclaude").command();
        var without = AgentLauncher.plan(prp, settings, "sid", "tab", "haiku", null).command();
        check("only the program differs", withExe.substring("fclaude".length()).equals(without.substring("claude".length())));

        settings.setAgentCommand("\"C:\\tools\\my agent.exe\" %FLAGS% \"%PROMPT%\"");
        check("a quoted program in the template is replaced whole",
                AgentLauncher.planWith(prp, settings, null, "pclaude").command().startsWith("pclaude --dangerously"));
        settings.setAgentCommand(null);

        check("the empty-agent command takes an exe",
                PrpPrompt.commandWithoutPrompt(settings, "s", "n", null, "fclaude").startsWith("fclaude "));

        var session = new PrpSession().sessionId("abc-123").tabName("t-01");
        check("prp resume on fclaude", PrpResume.command(session, settings, false, null, "fclaude")
                .startsWith("fclaude --resume abc-123"));
        check("prp resume without exe is claude",
                PrpResume.command(session, settings, false).startsWith("claude --resume abc-123"));
        var rec = new littlejlib.proj.hib.HibRecord().sessionId("def-456");
        check("hib restore on fclaude",
                littlejlib.proj.hib.Restorer.command(rec, settings, null, "fclaude").startsWith("fclaude --resume def-456"));
        var doc = new littlejlib.proj.cct.model.SessionDoc().sessionId("ghi-789");
        check("cct resume on fclaude", littlejlib.proj.cct.action.ResumeCommands
                .in(littlejlib.proj.cct.action.ShellKind.BASH, doc, false, false, null, "fclaude")
                .contains("fclaude --resume ghi-789"));
        check("cct bare on pclaude", littlejlib.proj.cct.action.ResumeCommands.bare(doc, "pclaude").equals("pclaude --resume ghi-789"));

        // A recorded exe is a question, an explicit one an answer, the default no question at all.
        var asked = new java.util.ArrayList<String>();
        var onFree = new PrpSession().sessionId("x").exe("fclaude");
        check("explicit exe wins without asking", "pclaude".equals(PrpResume.exeFor(onFree, "pclaude", e -> { asked.add(e); return true; }))
                && asked.isEmpty());
        check("recorded fclaude, yes", "fclaude".equals(PrpResume.exeFor(onFree, null, e -> { asked.add(e); return true; }))
                && asked.equals(java.util.List.of("fclaude")));
        check("recorded fclaude, no or timeout is plain claude", PrpResume.exeFor(onFree, null, e -> false) == null);
        asked.clear();
        check("a session on plain claude is never asked",
                PrpResume.exeFor(session, null, e -> { asked.add(e); return true; }) == null
                        && PrpResume.exeFor(new PrpSession().exe("claude"), null, e -> { asked.add(e); return true; }) == null
                        && asked.isEmpty());

        // Names.
        for (var bad : new String[] {"fclaude; calc", "..\\evil", "a b", "x/y", "\"q\""}) {
            try {
                AgentExe.check(bad);
                check("refused: " + bad, false);
            } catch (IllegalArgumentException e) {
                check("refused: " + bad, true);
            }
        }
        check("blank is no exe", AgentExe.check("  ") == null && AgentExe.check(null) == null);

        // PATH: something that exists everywhere we run.
        var present = System.getProperty("os.name", "").toLowerCase().contains("win") ? "cmd" : "sh";
        check("a program on PATH resolves without an extension", AgentExe.resolve(present) != null);
        check("a program not on PATH does not", AgentExe.resolve("no-such-claude-xyz") == null);
        try {
            AgentExe.require("no-such-claude-xyz");
            check("require refuses a program not on PATH", false);
        } catch (IllegalArgumentException e) {
            check("require refuses a program not on PATH", e.getMessage().contains("no program named no-such-claude-xyz"));
        }
        System.out.println("claude variants on PATH: " + AgentExe.candidates());
        System.exit(failures == 0 ? 0 : 1);
    }
}
