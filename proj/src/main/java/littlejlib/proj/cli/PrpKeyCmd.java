package littlejlib.proj.cli;

import littlejlib.proj.prp.ProjFile;
import littlejlib.proj.prp.ProjIcon;
import littlejlib.proj.prp.ProjNames;
import littlejlib.proj.prp.PrpSessions;
import littlejlib.proj.prp.TabName;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The project's {@code prp/proj.json}: its short name - the {@code billing} that makes PRP 38 of that
 * project {@code billing-38} - and the long name, icon and uuid beside it.
 * <p>
 * The short name behaves like a JIRA project key and is meant to: short, stable, said out loud, and
 * unique enough to identify one piece of work among every project on the machine. The terminal tab
 * name is that ticket with the task appended, so setting it also fixes what every future tab is called.
 */
@Command(name = "key", description = "Show or set this project's short name, long name and icon (prp/proj.json)")
public class PrpKeyCmd implements Runnable {

    @Parameters(arity = "0..1", description = "The short name to set. With none, prints the current values")
    String key;

    @Option(names = {"-d", "--dir"}, description = "Project directory, or its prp folder")
    String dir;

    @Option(names = "--long-name", description = "The project's long name")
    String longName;

    @Option(names = "--alias3", paramLabel = "<abc>", description = "A 3-letter alias, accepted wherever the short name is")
    String alias3;

    @Option(names = "--alias4", paramLabel = "<abcd>", description = "A 4-letter alias")
    String alias4;

    @Option(names = "--icon", description = "Icon: a path relative to the project, or an http(s) URL. Empty clears it")
    String icon;

    @Override
    public void run() {
        var project = PrpCommands.projectDir(dir);
        var clean = key == null || key.isBlank() ? null : TabName.cleanKey(key);
        if (key != null && !key.isBlank() && clean.isEmpty()) {
            PrpCommands.ERR.println("a key needs at least one letter or digit");
            System.exit(2);
            return;
        }
        var owner = clean == null ? null : PrpSessions.keyOwner(clean, project);
        if (owner != null) PrpCommands.ERR.println("note: " + clean + " is also used by " + owner);

        var problems = new java.util.ArrayList<String>();
        problems.add(alias3 == null ? null : ProjNames.alias(ProjNames.ALIAS3, alias3, 3, k -> PrpSessions.keyOwner(k, project), true));
        problems.add(alias4 == null ? null : ProjNames.alias(ProjNames.ALIAS4, alias4, 4, k -> PrpSessions.keyOwner(k, project), true));
        problems.removeIf(java.util.Objects::isNull);
        if (!problems.isEmpty()) {
            problems.forEach(PrpCommands.ERR::println);
            System.exit(2);
            return;
        }

        if (clean != null || longName != null || icon != null || alias3 != null || alias4 != null) {
            ProjFile.update(project, p -> {
                if (clean != null) p.name(clean);
                if (alias3 != null) p.alias3(alias3.strip().toLowerCase());
                if (alias4 != null) p.alias4(alias4.strip().toLowerCase());
                if (longName != null) p.longName(longName.isBlank() ? null : longName.strip());
                if (icon != null) p.icon(icon.isBlank() ? null : icon.strip());
            });
            if (icon != null) ProjIcon.refresh(project, ProjFile.ensure(project), true);
        }
        ProjFile.prepare(project);
        show(project);
    }

    static void show(java.nio.file.Path project) {
        var p = ProjFile.read(project);
        var shown = ProjIcon.current(project, p);
        PrpCommands.OUT.println("name       " + p.name());
        PrpCommands.OUT.println("alias3     " + (p.alias3() == null ? "" : p.alias3()));
        PrpCommands.OUT.println("alias4     " + (p.alias4() == null ? "" : p.alias4()));
        PrpCommands.OUT.println("longName   " + (p.longName() == null ? "" : p.longName()));
        PrpCommands.OUT.println("uuid       " + p.uuid());
        PrpCommands.OUT.println("icon       " + (p.icon() == null ? "" : p.icon()));
        PrpCommands.OUT.println("shown      " + (shown == null ? "none" : shown));
    }
}
