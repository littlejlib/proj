package littlejlib.proj.migrate;

import module java.base;

import littlejlib.proj.hib.HibRecord;
import littlejlib.proj.prp.PrpSession;

/** Plans the new path, number and tab name of session rows and hibernated records, from one folder's map. */
public final class Rewriter {

    final FolderMap map;

    public Rewriter(FolderMap map) {
        this.map = map;
    }

    /** The map entry of the PRP a row points at, by its file name, else by its number; null when it is not this folder's or already new. */
    MapEntry target(String prpFile, String prpIndex) {
        if (prpFile == null || prpFile.isBlank() || !map.owns(prpFile)) return null;
        var name = FolderMap.nameOf(prpFile);
        if (map.isNewMain(name)) return null;
        var e = map.mainByFile(name);
        if (e != null || prpIndex == null || prpIndex.isBlank()) return e;
        return map.mainByNumber(prpIndex);
    }

    public Move forRow(PrpSession s) {
        var e = target(s.prpFile(), s.prpIndex());
        if (e == null) return null;
        return new Move().id(s.sessionId()).fromFile(s.prpFile()).toFile(map.dir().resolve(e.newFile()).toString())
                .fromIndex(s.prpIndex()).toIndex(e.newNumber()).fromName(s.tabName())
                .toName(tab(s.tabName(), e.oldNumber(), map.shortOf(e))).how(how(s.prpFile(), e));
    }

    public Move forHib(HibRecord r) {
        var e = target(r.prpFile(), r.prpIndex());
        if (e == null) return null;
        var log = r.logFile() == null || !map.owns(r.logFile()) ? null : map.byOldFile(FolderMap.nameOf(r.logFile()));
        return new Move().id(r.sessionId()).fromFile(r.prpFile()).toFile(map.dir().resolve(e.newFile()).toString())
                .fromIndex(r.prpIndex()).toIndex(e.newNumber()).fromName(r.name())
                .toName(tab(r.name(), e.oldNumber(), map.shortOf(e))).fromLog(r.logFile())
                .toLog(log == null ? r.logFile() : map.dir().resolve(log.newFile()).toString()).how(how(r.prpFile(), e));
    }

    static String how(String prpFile, MapEntry e) {
        return FolderMap.nameOf(prpFile).equals(e.oldFile()) ? "file" : "number";
    }

    /** Where {@code -<old>-} (or a trailing {@code -<old>}) sits in a tab name, after at least one character of key; -1 when absent. */
    static int markerAt(String name, String oldNumber) {
        if (name == null || oldNumber == null || oldNumber.isBlank()) return -1;
        var lower = name.toLowerCase(Locale.ROOT);
        var mid = ("-" + oldNumber + "-").toLowerCase(Locale.ROOT);
        var at = lower.indexOf(mid);
        if (at > 0) return at;
        var end = ("-" + oldNumber).toLowerCase(Locale.ROOT);
        return lower.endsWith(end) && name.length() > end.length() ? name.length() - end.length() : -1;
    }

    /** The tab name with its old number replaced by the short new one; unchanged when it carries no old number. */
    public static String tab(String name, String oldNumber, String shortNew) {
        var at = markerAt(name, oldNumber);
        if (at < 0) return name;
        var rest = name.substring(at + 1 + oldNumber.length());
        return name.substring(0, at) + "-" + shortNew + rest;
    }
}
