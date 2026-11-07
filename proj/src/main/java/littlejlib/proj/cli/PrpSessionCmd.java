package littlejlib.proj.cli;

import littlejlib.proj.prp.PrpSessionStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

/**
 * The recorded PRP sessions, served where the database actually is.
 * <p>
 * Registered under {@code query} so it rides the daemon route: a CLI process cannot open the index
 * while the daemon holds the lock, and this is how {@code proj prp resume} gets its answer anyway.
 * Machine-facing - one bare line for a name, TSV for a list - because its only caller is
 * {@code prp/PrpSessions}.
 */
@Command(name = "prp-session", description = "The claude-code sessions recorded against a PRP")
public class PrpSessionCmd implements Runnable {

    @Parameters(index = "0", description = "name | list | record | key | key-owner | set-key")
    String verb;

    @Parameters(index = "1..*", description = "arguments of the verb")
    List<String> rest;

    final PrintWriter out, err;

    public PrpSessionCmd(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    @Override
    public void run() {
        var args = rest == null ? List.<String>of() : rest;
        var store = new PrpSessionStore();
        switch (verb == null ? "" : verb) {
            case "name" -> {
                need(args, 1);
                out.println(store.tabName(Path.of(args.get(0))));
            }
            case "list" -> {
                need(args, 1);
                for (var s : store.of(Path.of(args.get(0))))
                    out.println(String.join("\t", nz(s.sessionId()), nz(s.tabName()),
                            String.valueOf(s.started()), nz(s.prpIndex()), nz(s.projectPath()),
                            nz(s.exe())));
            }
            case "record" -> {
                need(args, 5);
                store.record(args.get(0), Path.of(args.get(1)), blankToNull(args.get(2)),
                        Path.of(args.get(3)), blankToNull(args.get(4)), System.currentTimeMillis(),
                        args.size() > 5 ? blankToNull(args.get(5)) : null);
                out.println("ok");
            }
            case "key" -> {
                need(args, 1);
                var key = new littlejlib.proj.prp.ProjectKeyStore().of(Path.of(args.get(0)));
                if (key != null) out.println(key);
            }
            case "key-owner" -> {
                need(args, 2);
                var owner = new littlejlib.proj.prp.ProjectKeyStore()
                        .ownerOf(args.get(0), Path.of(args.get(1)));
                if (owner != null) out.println(owner);
            }
            case "set-key" -> {
                need(args, 2);
                new littlejlib.proj.prp.ProjectKeyStore().set(Path.of(args.get(0)), args.get(1));
                out.println("ok");
            }
            default -> {
                err.println("unknown verb: " + verb
                        + " (name | list | record | key | key-owner | set-key)");
                throw new IllegalArgumentException("unknown verb");
            }
        }
    }

    void need(List<String> args, int count) {
        if (args.size() < count)
            throw new IllegalArgumentException(verb + " needs " + count + " argument(s)");
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
