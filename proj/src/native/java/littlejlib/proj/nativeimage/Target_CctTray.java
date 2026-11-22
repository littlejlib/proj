package littlejlib.proj.nativeimage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import java.util.List;

@TargetClass(className = "littlejlib.proj.cct.ui.Tray")
final class Target_CctTray {
    @Substitute
    public boolean install(Runnable onShow, Runnable onQuit) {
        NativeTray.transcripts = NativeTray.start("transcripts - search past Claude Code conversations", onShow,
                List.of("Show transcripts", "-", "Quit"), List.of(onShow, onQuit, onQuit));
        return NativeTray.transcripts != null;
    }

    @Substitute
    public void note(String title, String body) {
        if (NativeTray.transcripts != null) NativeTray.transcripts.balloon(title, body);
    }

    @Substitute
    public void remove() {
        if (NativeTray.transcripts != null) NativeTray.transcripts.stop();
    }
}
