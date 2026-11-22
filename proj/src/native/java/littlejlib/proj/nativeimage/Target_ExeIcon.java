package littlejlib.proj.nativeimage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.nio.file.Path;
import javafx.scene.image.Image;

@TargetClass(className = "littlejlib.proj.ide.ExeIcon")
final class Target_ExeIcon {
    @Substitute
    static Image extract(Path exe, int size) { return null; }
}
