package littlejlib.proj.model;

import xyz.jphil.arcadedb.datahelper.ArcadeData;
import xyz.jphil.arcadedb.datahelper.TypeDef;

/**
 * One source file, one row. Names and locations only - never contents.
 * <p>
 * WHAT THIS IS NOT FOR, measured rather than assumed. It does NOT beat ripgrep at finding a file by
 * name. On this machine, over this code base, {@code rg --files -g "TerminalRunner.java"} takes
 * about 0.95s and {@code proj files --exact TerminalRunner} about 1.28s warm, with an AOT cache and
 * a running daemon - because ripgrep's directory walk is fast enough that JVM startup alone loses
 * the race. Anyone reaching for this expecting a speed win should use ripgrep instead.
 * <p>
 * WHAT IT IS FOR is the things a path alone does not tell you: which of 705 projects owns the file,
 * its package and fully qualified name, and whether it is in your own code or in a third-party
 * mirror - so a lookup can be restricted to one project or one base. ripgrep returns a path and
 * leaves the attribution to the reader; over a tree of many independent repositories that
 * attribution is most of the answer.
 * <p>
 * {@code --paths} still composes with ripgrep for the case where narrowing genuinely pays: searching
 * the CONTENTS of one project's sources without walking the other 704.
 * <p>
 * {@code stem} is the filename without its extension, which for Java and most of its neighbours is
 * the type name - so it is the column a class lookup hits, and it is indexed.
 */
@ArcadeData
public final class FileRecord extends FileRecord_A {

    String path;            // absolute, and the identity of the row
    String projectPath;     // the indexed project this file belongs to
    String name;            // "TerminalRunner.java"
    String stem;            // "TerminalRunner" - the type name, for languages that agree with that
    String ext;             // "java", lowercased, without the dot
    String relPath;         // relative to the project, which is what a human recognises
    String pkg;             // dotted package, when the layout makes it derivable; else ""
    Long lastModified;

    public static final TypeDef<FileRecord> TYPEDEF =
            schemaBuilder()
                    .factory(FileRecord::new)
                    .unique($path)
                    .lsmIndex($stem)
                    .lsmIndex($projectPath)
                    .lsmIndex($ext)
                    .__();

    /** The fully qualified name, when there is a package to qualify it with. */
    public String fqn() {
        return pkg == null || pkg.isBlank() ? nz(stem) : pkg + "." + stem;
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
