package littlejlib.proj.nativeimage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.util.List;

@TargetClass(className = "littlejlib.proj.daemon.TrayUi")
final class Target_TrayUi {
    @Substitute
    public boolean install(Runnable onOpen, Runnable onRescan, Runnable onExit) {
        NativeTray.daemon = NativeTray.start("proj - project tools", onOpen,
                List.of("Open project picker", "Rescan now", "-", "Exit"), List.of(onOpen, onRescan, onExit, onExit));
        return NativeTray.daemon != null;
    }

    @Substitute
    public void note(String message) {
        if (NativeTray.daemon != null) NativeTray.daemon.tooltip("proj - " + message);
    }

    @Substitute
    public void remove() {
        if (NativeTray.daemon != null) NativeTray.daemon.stop();
    }
}
