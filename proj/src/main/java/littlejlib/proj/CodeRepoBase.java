package littlejlib.proj;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** One directory holding many code repositories, and what kind of coderepobase it is. */
public class CodeRepoBase {

    private String path;
    private CodeRepoBaseType type = CodeRepoBaseType.AUXILIARY;

    public CodeRepoBase() { }

    public CodeRepoBase(String path, CodeRepoBaseType type) {
        this.path = path;
        this.type = type == null ? CodeRepoBaseType.AUXILIARY : type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public CodeRepoBaseType getType() {
        return type;
    }

    public void setType(CodeRepoBaseType type) {
        this.type = type == null ? CodeRepoBaseType.AUXILIARY : type;
    }

    public Path toPath() {
        return path == null || path.isBlank() ? null : Paths.get(path.trim());
    }

    public boolean exists() {
        var p = toPath();
        return p != null && Files.isDirectory(p);
    }

    public boolean sameLocationAs(Path other) {
        var mine = toPath();
        return mine != null && other != null
                && mine.toAbsolutePath().normalize().equals(other.toAbsolutePath().normalize());
    }

    @Override
    public String toString() {
        return type.slug() + "  " + path;
    }
}
