package littlejlib.proj.cct.export;

import java.util.List;

/**
 * What the caller knows about WHY this log is being written, which the session itself cannot say: the
 * PRP it belongs to, the files a later session should read alongside it, and which export in the
 * series this is.
 *
 * @param title  the first line of the file
 * @param serial e.g. "03 of prp 29", or null when this is not a PRP log
 * @param lines  free-form facts printed above the session's own, one per line, never wrapped
 */
public record LogHeading(String title, String serial, List<String> lines) {

    public static LogHeading plain(String title) {
        return new LogHeading(title, null, List.of());
    }
}
