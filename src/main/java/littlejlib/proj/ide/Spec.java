package littlejlib.proj.ide;

import java.util.List;

/**
 * How to recognise and invoke one tool: the executable names to look for, the argument template,
 * and whether it can open a folder as a project.
 */
public class Spec {

    public final List<String> executables;
    public final String args;
    public final boolean folders;

    public Spec(List<String> executables, String args, boolean folders) {
        this.executables = executables;
        this.args = args;
        this.folders = folders;
    }
}
