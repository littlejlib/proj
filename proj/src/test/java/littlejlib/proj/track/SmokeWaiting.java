package littlejlib.proj.track;

import littlejlib.proj.prp.PrpUuid;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Open work items: of one PRP, of one project, and of several projects at once.
 *
 * mvn test-compile exec:java -Dexec.mainClass="littlejlib.proj.track.SmokeWaiting" -Dexec.classpathScope=test
 */
public class SmokeWaiting {

    public static void main(String[] args) throws Exception {
        var root = Files.createTempDirectory("smokewaiting");
        var billing = Files.createDirectories(root.resolve("billing").resolve("prp"));
        var ledger = Files.createDirectories(root.resolve("ledger").resolve("prp"));
        Files.writeString(billing.resolve("000100-export.md"), "- PRP Number: 0001\n- PRP Name: export\n");
        Files.writeString(billing.resolve("000200-import.md"), "- PRP Number: 0002\n- PRP Name: import\n");
        var u1 = PrpUuid.ensure(billing.resolve("000100-export.md"), false).uuid();
        var u2 = PrpUuid.ensure(billing.resolve("000200-import.md"), false).uuid();
        Workplan.add(billing, new Item().type("todo").title("write the exporter").prp(u1));
        Workplan.add(billing, new Item().type("confirm").title("comma or semicolon?").prp(u1));
        Workplan.add(billing, new Item().type("discuss").title("archive old invoices").prp(u2).related(List.of(u1)));
        var done = Workplan.add(billing, new Item().type("todo").title("set up the folder").prp(u1));
        Workplan.set(billing, done.id(), i -> i.status("done"), null);
        Workplan.add(ledger, new Item().type("confirm").title("which year first?"));

        var open1 = Waiting.open(billing, u1);
        check(open1.size() == 3 && open1.get(0).title().equals("comma or semicolon?"), "PRP 1: owned + related, waiting first: " + open1);
        check(open1.stream().noneMatch(i -> "done".equals(i.status())), "done items hidden");
        check(Waiting.open(billing, u2).size() == 1, "PRP 2: only its own item");
        check(Waiting.open(billing, null).stream().filter(Waiting::forUser).count() == 2, "two wait for the user in the project");

        var projects = new LinkedHashMap<java.nio.file.Path, String>();
        projects.put(billing.getParent(), "billing");
        projects.put(ledger.getParent(), "ledger");
        var all = Waiting.everywhere(projects, false);
        check(all.size() == 4 && all.get(0).contains("confirm") && all.get(all.size() - 1).contains("todo"), "everywhere, waiting first: " + all);
        check(Waiting.everywhere(projects, true).size() == 3 && all.stream().anyMatch(l -> l.startsWith("ledger  #1")), "for the user only, keys shown");
        System.out.println("  pass  waiting: per PRP, per project, everywhere");
        System.out.println("ok");
        System.exit(0);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
