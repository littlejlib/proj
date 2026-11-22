package littlejlib.proj.renumber;

import java.util.List;
import java.util.regex.Pattern;

import static littlejlib.proj.prp.PrpFiles.OLD_MAIN;

/**
 * Which file is the PRP when one number has several main-looking files: the only open one, else the newest closed one by
 * name order. The others become sub-files of it; several open ones cannot be decided and refuse the folder.
 */
final class MainPick {

    private MainPick() { }

    static final Pattern MAIN_LIKE = Pattern.compile("^([0-9A-Z]{2})-prp-(.+)$");

    /** The main file of these files of PRP {@code old}, or null when there is none. */
    static String pick(String old, List<String> files, List<String> dirs) {
        var mains = files.stream().filter(n -> !dirs.contains(n) && OLD_MAIN.matcher(n).matches()).sorted().toList();
        if (mains.size() <= 1) return mains.isEmpty() ? null : mains.get(0);
        var open = mains.stream().filter(n -> !n.endsWith(".closed.md")).toList();
        if (open.size() > 1) throw new IllegalStateException("PRP " + old + " has several open main files: " + String.join(", ", open));
        return open.size() == 1 ? open.get(0) : mains.get(mains.size() - 1);
    }

    /** The words of a main-looking file that is not the main: everything after {@code NN-prp-}. */
    static String words(String name) {
        var m = MAIN_LIKE.matcher(name);
        return m.matches() ? m.group(2) : null;
    }
}
