package littlejlib.proj.migrate;

import datapotter.datahelper.Data;

/** What changes in one session row or hibernated record, as planned from a folder's map. */
@Data
public final class Move extends Move_A {

    String id;
    String fromFile;
    String toFile;
    String fromIndex;
    String toIndex;
    String fromName;
    String toName;
    String fromLog;
    String toLog;
    String how;

    public boolean changed() {
        return !same(fromFile, toFile) || !same(fromIndex, toIndex) || !same(fromName, toName) || !same(fromLog, toLog);
    }

    static boolean same(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
