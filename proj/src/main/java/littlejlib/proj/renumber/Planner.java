package littlejlib.proj.renumber;

import littlejlib.proj.prp.PrpFiles;
import littlejlib.proj.prp.PrpNumber;
import littlejlib.proj.prp.PrpUuid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static littlejlib.proj.prp.PrpFiles.*;

/**
 * Decision 3 and 4 of the renumbering: the n-th PRP of a folder (legacy order) becomes base34(n), a legacy 00 becomes 0000;
 * main to slot 00, status and meta to slot 01, sub-files and sub-directories by (serial, name) to slots 02.., logs by
 * serial to ZZ-01... A main-looking file that is not the PRP (not markdown, or a second main) becomes a sub-file.
 */
public final class Planner {

    private Planner() { }

    static final Pattern LOOKS = Pattern.compile("(?i)^[0-9a-z]{1,3}-prp[-.]"), SERIAL = Pattern.compile("^(\\d+)\\.(.+)$"),
            OLD_DIR = Pattern.compile("^" + PrpNumber.LEGACY + "-prp\\..+$");
    static final int LAST_SUB = PrpNumber.value("ZY");

    /** @throws IllegalStateException when the folder cannot be renumbered mechanically, saying why */
    public static RenumberPlan plan(Path prpDir) throws IOException {
        var names = list(prpDir, Files::isRegularFile);
        var dirs = list(prpDir, Files::isDirectory).stream().filter(n -> OLD_DIR.matcher(n).matches()).toList();
        var plan = new RenumberPlan();
        var bad = names.stream().filter(n -> !projectFile(n) && LOOKS.matcher(n).find() && !isLegacy(n)).toList();
        if (!bad.isEmpty()) throw new IllegalStateException("files that look like PRP files but do not parse: " + String.join(", ", bad));
        var legacy = new ArrayList<>(names.stream().filter(PrpFiles::isLegacy).toList());
        if (legacy.isEmpty()) return plan;
        legacy.addAll(dirs);
        var fresh = names.stream().filter(PrpFiles::isNew).toList();
        if (!fresh.isEmpty()) throw new IllegalStateException("holds both schemes, e.g. " + fresh.get(0) + " beside " + legacy.get(0));
        var byNumber = new TreeMap<String, List<String>>(Comparator.comparingInt(PrpNumber::ordinal));
        for (var n : legacy) byNumber.computeIfAbsent(numberOf(n), k -> new ArrayList<>()).add(n);
        var next = byNumber.containsKey("00") ? 0 : 1;
        var refused = new ArrayList<String>();
        for (var e : byNumber.entrySet()) {
            var newNumber = PrpNumber.of(next++);
            plan.numbers().put(e.getKey(), newNumber);
            try {
                one(prpDir, e.getKey(), newNumber, e.getValue(), dirs, plan);
            } catch (IllegalStateException x) {
                refused.add(x.getMessage());
            }
        }
        if (!refused.isEmpty()) throw new IllegalStateException(String.join("; ", refused));
        var taken = new HashSet<>(names);
        taken.addAll(dirs);
        for (var f : plan.files())
            if (!taken.add(f.newFile())) throw new IllegalStateException("two files would be called " + f.newFile());
        return plan;
    }

    static String numberOf(String name) {
        var n = PrpFiles.numberOf(name);
        return n != null ? n : name.substring(0, name.indexOf("-prp"));
    }

    static void one(Path prpDir, String old, String nu, List<String> files, List<String> dirs, RenumberPlan plan) {
        var main = MainPick.pick(old, files, dirs);
        var uuid = main == null ? "" : Objects.requireNonNullElse(PrpUuid.read(prpDir.resolve(main)), "");
        if (main == null) plan.notes().add("PRP " + old + " has no main file (orphan files); they become " + nu);
        else plan.mains().put(main, uuid);
        var subs = new ArrayList<String[]>();
        var logs = new ArrayList<String[]>();
        for (var n : files) {
            var m = OLD_MAIN.matcher(n);
            if (n.equals(main) && m.matches()) {
                add(plan, n, main(nu, m.group(2), m.group(3) != null), old, nu, "main", uuid);
                continue;
            }
            var dir = dirs.contains(n);
            if (!dir && (m = OLD_DATA.matcher(n)).matches()) {
                add(plan, n, nu + DATA_SLOT + "." + m.group(2), old, nu, "data", uuid);
                continue;
            }
            if (!dir && (m = OLD_LOG.matcher(n)).matches()) {
                logs.add(new String[]{m.group(2), m.group(3), n});
                continue;
            }
            var words = MainPick.words(n);
            if (words != null) {
                plan.notes().add(n + " is not the main file of PRP " + old + ": it becomes a sub-file");
                subs.add(new String[]{"" + Integer.MAX_VALUE, words, n, "sub"});
                continue;
            }
            if (!(m = OLD_SUB.matcher(n)).matches()) throw new IllegalStateException("cannot place " + n);
            var s = SERIAL.matcher(m.group(2));
            var kind = dir ? "dir" : "sub";
            subs.add(s.matches() ? new String[]{s.group(1), s.group(2), n, kind} : new String[]{"" + Integer.MAX_VALUE, m.group(2), n, kind});
        }
        subs.sort(Comparator.<String[]>comparingLong(a -> Long.parseLong(a[0])).thenComparing(a -> a[2]));
        var slot = 2;
        for (var s : subs) {
            if (slot > LAST_SUB) throw new IllegalStateException("PRP " + old + " has more sub-files than slots 02..ZY");
            var tail = s[1].contains(".") || s[3].equals("dir") ? s[1] : "note." + s[1];
            add(plan, s[2], nu + PrpNumber.base34(slot++, 2) + "-" + tail, old, nu, s[3], uuid);
        }
        var serials = logs.stream().map(a -> Integer.parseInt(a[0])).distinct().sorted().toList();
        logs.sort(Comparator.<String[]>comparingInt(a -> Integer.parseInt(a[0])).thenComparing(a -> a[2]));
        for (var l : logs)
            add(plan, l[2], log(nu, serials.indexOf(Integer.parseInt(l[0])) + 1, l[1].toLowerCase()), old, nu, "log", uuid);
    }

    static void add(RenumberPlan plan, String oldFile, String newFile, String old, String nu, String kind, String uuid) {
        plan.files().add(new RenumberFile().oldFile(oldFile).newFile(newFile).oldNumber(old).newNumber(nu).kind(kind).uuid(uuid));
    }

    static List<String> list(Path prpDir, Predicate<Path> which) throws IOException {
        try (var files = Files.list(prpDir)) {
            return files.filter(which).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }
}
