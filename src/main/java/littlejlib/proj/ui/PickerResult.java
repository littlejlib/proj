package littlejlib.proj.ui;

import littlejlib.proj.model.ProjectRecord;

/**
 * What the picker hands back. Carries the whole record now, not just a path string, because the
 * caller usually wants the maven coordinates or the absolute directory too.
 */
public class PickerResult {

    public final ProjectRecord record;
    public final ProjectPickerDialog.Action action;

    public PickerResult(ProjectRecord record, ProjectPickerDialog.Action action) {
        this.record = record;
        this.action = action;
    }

    /** Relative to the code directory, which is what the terminal-side code resolves against. */
    public String projectPath() {
        return record == null ? "" : record.relativePath();
    }

    public java.nio.file.Path absolutePath() {
        return record == null ? null : record.dir();
    }
}
