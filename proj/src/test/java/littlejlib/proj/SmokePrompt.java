package littlejlib.proj;

import littlejlib.proj.prp.GlobalPrpEntry;
import littlejlib.proj.prp.PrpLogs;
import littlejlib.proj.prp.PrpPrompt;

import java.nio.file.Path;

/**
 * Prints the instruction and the full command line that would be handed to the agent for every PRP
 * of a project, without starting anything.
 * <p>
 * The three clauses of that prompt are each conditional on a file existing, and getting one wrong
 * produces a command that looks perfectly reasonable and sends the agent after a file that is not
 * there. That is invisible from the source and obvious from the output.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.SmokePrompt" -Dexec.classpathScope=test -Dexec.args="[projectDir]"
 */
public class SmokePrompt {

    public static void main(String[] args) throws Exception {
        var project = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        var settings = new SettingsManager().loadSettings();

        System.out.println("project           " + project);
        System.out.println("template          " + littlejlib.proj.prp.AgentLauncher.templateOf(settings));
        System.out.println("skip permissions  " + settings.isAgentSkipPermissions());
        System.out.println("log tail          " + settings.getAgentLogsTailPercent() + "%");
        System.out.println("summon key        "
                + littlejlib.proj.util.GlobalHotkey.describe(settings.getGlobalHotkey())
                + "   (stored: " + settings.getGlobalHotkey() + ")");

        var stored = settings.getPrpTemplateSrc();
        var template = PRPTemplateLoader.loadTemplate(stored);
        System.out.println("template stored   " + stored);
        System.out.println("template used     " + PRPTemplateLoader.effectiveSrc(stored)
                + "   (" + template.length() + " chars, first line: "
                + template.lines().findFirst().orElse("") + ")");
        System.out.println();

        for (var e : GlobalPrpEntry.forProject(project)) {
            var sessions = PrpLogs.sessionsOf(e.getPrpDir(), e.getIndex());
            System.out.println(e.getIndex() + "  status=" + (e.getPrpEntry().hasStatusFile() ? "yes" : "no ")
                    + "  logs=" + sessions.size()
                    + (sessions.isEmpty() ? "" : " (latest " + sessions.get(sessions.size() - 1).index() + ")")
                    + "  next export -> " + PrpLogs.nextName(e.getPrpDir(), e.getIndex()));
            System.out.println("    prompt   " + PrpPrompt.of(e.getMainFile(), settings));
            System.out.println("    command  " + PrpPrompt.command(e.getMainFile(), settings));
        }

        System.out.println();
        System.out.println("empty-agent command  " + PrpPrompt.commandWithoutPrompt(settings));
    }
}
