package littlejlib.proj.nativeimage;

import littlejlib.proj.ui.AppIcon;
import littlejlib.proj.util.WinTray;

import java.util.List;

final class NativeTray {
    private NativeTray() { }

    static WinTray daemon, transcripts;

    static WinTray start(String tip, Runnable onClick, List<String> labels, List<Runnable> actions) {
        var tray = new WinTray(tip, px -> AppIcon.stream(AppIcon.PROJECTS, px), onClick, labels, actions);
        return tray.start() ? tray : null;
    }
}
