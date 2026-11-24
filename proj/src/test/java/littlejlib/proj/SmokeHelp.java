package littlejlib.proj;

import littlejlib.proj.cli.CliWiring;
import littlejlib.proj.cli.QueryCommands;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * PRP 001W: every command answers {@code --help} with its usage (exit 0), and a wrong option gets a short error
 * naming it (non-zero exit). Walks both picocli trees: the main one and the query commands it forwards to.
 * Exits 1 on any failure.
 * <p>
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokeHelp" -Dexec.classpathScope=test
 */
public class SmokeHelp {

    record Run(int code, String out, String err) { }

    public static void main(String[] a) {
        var failures = new ArrayList<String>();
        var checked = 0;

        for (var path : paths(CliWiring.wire(new CommandLine(new Main()), sink(), sink()), true)) {
            var r = main(plus(path, "--help"));
            checked++;
            // the root prints its own hand-written page to the console, not through picocli: exit code only
            var usage = path.isEmpty() || r.out.contains("Usage: proj");
            if (r.code != 0 || !usage) failures.add("proj " + String.join(" ", path) + " --help -> exit " + r.code + ", out " + r.out.length() + " chars, err " + r.err.strip());
        }
        for (var path : paths(queryTree(), false)) {
            var r = query(plus(path, "--help"));
            checked++;
            if (r.code != 0 || !r.out.contains("Usage: proj")) failures.add("proj " + String.join(" ", path) + " --help (query) -> exit " + r.code + ", err " + r.err.strip());
        }

        var bad = main(List.of("prp", "plan", "add", "--bogus", "title"));
        checked++;
        var lines = bad.err.strip().lines().toList();
        if (bad.code == 0 || lines.isEmpty() || lines.size() > 3 || !bad.err.contains("--bogus"))
            failures.add("prp plan add --bogus -> exit " + bad.code + ", err: " + bad.err);
        var badQuery = query(List.of("list", "--bogus"));
        checked++;
        if (badQuery.code == 0 || !badQuery.err.contains("--bogus")) failures.add("list --bogus -> exit " + badQuery.code + ", err: " + badQuery.err);

        failures.forEach(f -> System.out.println("FAIL " + f));
        System.out.println(checked + " checked, " + failures.size() + " failed");
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    static Run main(List<String> args) {
        var out = new StringWriter();
        var err = new StringWriter();
        var code = CliWiring.wire(new CommandLine(new Main()), new PrintWriter(out, true), new PrintWriter(err, true))
                .execute(args.toArray(String[]::new));
        return new Run(code, out.toString(), err.toString());
    }

    static Run query(List<String> args) {
        var out = new StringWriter();
        var err = new StringWriter();
        var code = QueryCommands.run(args, new PrintWriter(out, true), new PrintWriter(err, true));
        return new Run(code, out.toString(), err.toString());
    }

    static CommandLine queryTree() {
        return QueryCommands.tree(sink(), sink());
    }

    /** Every command's path below the root, aliases once; with skipForwarding, not the ones that hand their args on. */
    static List<List<String>> paths(CommandLine root, boolean skipForwarding) {
        var out = new ArrayList<List<String>>();
        out.add(List.of());
        walk(root, List.of(), new IdentityHashMap<>(), out, skipForwarding);
        return out;
    }

    static void walk(CommandLine c, List<String> path, IdentityHashMap<CommandLine, Boolean> seen,
                     List<List<String>> out, boolean skipForwarding) {
        for (var sub : c.getSubcommands().values()) {
            if (seen.put(sub, true) != null) continue;
            var p = plus(path, sub.getCommandName());
            if (!(skipForwarding && !sub.getCommandSpec().unmatchedArgsBindings().isEmpty())) out.add(p);
            walk(sub, p, seen, out, skipForwarding);
        }
    }

    static List<String> plus(List<String> path, String s) {
        var l = new ArrayList<>(path);
        l.add(s);
        return l;
    }

    static PrintWriter sink() {
        return new PrintWriter(new StringWriter());
    }
}
