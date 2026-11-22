package littlejlib.proj.nativeimage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.nio.file.Path;

@TargetClass(className = "littlejlib.proj.prp.ProjIcon")
final class Target_ProjIcon {
    @Substitute
    static boolean reencode(byte[] body, Path to) { return false; }
}
