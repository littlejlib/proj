package littlejlib.proj;

import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.prp.PrpPrompt;

import java.nio.file.Files;

/**
 * PRP 51: the agent command carries {@code --model <m>} when asked and nothing when not. Exits 1 on any miss.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokeAgentModel" -Dexec.classpathScope=test
 */
public class SmokeAgentModel {

    static int failures;

    static void check(String what, boolean ok) {
        System.out.println((ok ? "ok    " : "FAIL  ") + what);
        if (!ok) failures++;
    }

    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("smoke-agent-model");
        var prpDir = Files.createDirectory(dir.resolve("prp"));
        var prp = Files.writeString(prpDir.resolve("01-prp-demo.md"), "# demo\n");
        var settings = new Settings();
        settings.setAgentSkipPermissions(true);

        var haiku = AgentLauncher.plan(prp, settings, "haiku").command();
        var none = AgentLauncher.plan(prp, settings, (String) null).command();
        System.out.println("haiku  " + haiku);
        System.out.println("none   " + none);

        check("--model haiku is in the command", haiku.contains(" --model haiku "));
        check("no --model without the option", !none.contains("--model"));
        check("no %MODEL% left behind", !haiku.contains("%MODEL%") && !none.contains("%MODEL%"));
        check("no double space without the option", !none.contains("  "));

        settings.setAgentSkipPermissions(false);
        var noFlags = AgentLauncher.plan(prp, settings, "opus").command();
        check("works with permission prompts on", noFlags.startsWith("claude --model opus --session-id "));

        settings.setAgentCommand("claude %FLAGS% --session-id %SESSION% --name \"%NAME%\" \"%PROMPT%\"");
        check("a settings file holding the previous default gets the placeholder",
                AgentLauncher.plan(prp, settings, "sonnet").command().contains("--model sonnet"));

        check("the empty-agent command has no model", !PrpPrompt.commandWithoutPrompt(settings).contains("--model"));

// The other places a model is chosen: resume of a PRP's session, hib restore, cct resume.
var session = new littlejlib.proj.prp.PrpSession().sessionId("abc-123").tabName("t-01");
var resumed = littlejlib.proj.prp.PrpResume.command(session, settings, false, "haiku");
check("prp resume carries --model haiku", resumed.contains(" --resume abc-123 --model haiku"));
check("prp resume without a model has none",
        !littlejlib.proj.prp.PrpResume.command(session, settings, false).contains("--model"));
var rec = new littlejlib.proj.hib.HibRecord().sessionId("def-456");
check("hib restore carries --model sonnet",
        littlejlib.proj.hib.Restorer.command(rec, settings, "sonnet").contains(" --resume def-456 --model sonnet"));
check("hib restore without a model has none",
        !littlejlib.proj.hib.Restorer.command(rec, settings).contains("--model"));
var doc = new littlejlib.proj.cct.model.SessionDoc().sessionId("ghi-789");
check("cct resume carries --model opus", littlejlib.proj.cct.action.ResumeCommands
        .in(littlejlib.proj.cct.action.ShellKind.BASH, doc, false, false, "opus").contains("--resume ghi-789 --model opus"));
check("the empty-agent command takes a model too",
        PrpPrompt.commandWithoutPrompt(settings, "s", "n", "haiku").contains("--model haiku"));

try {
    AgentLauncher.plan(prp, settings, "haiku; calc");
            check("a model that is not a plain name is refused", false);
        } catch (IllegalArgumentException e) {
            check("a model that is not a plain name is refused", true);
        }
        System.exit(failures == 0 ? 0 : 1);
    }
}
