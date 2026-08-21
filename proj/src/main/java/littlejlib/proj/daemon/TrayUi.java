package littlejlib.proj.daemon;

import littlejlib.proj.ui.AppIconArt;

import java.awt.*;

/**
 * The tray presence of the resident daemon.
 * <p>
 * The icon is added once at startup and stays. mm added it in hideToSystemTray and removed it in
 * showFromSystemTray, so it only existed while the window was hidden - which is exactly backwards
 * for a process whose whole point is being permanently available.
 * <p>
 * The mark itself is {@link AppIconArt}, shared with every window, and drawn at the size the tray
 * actually asks for: a 16px image stretched to the 24px of a 150% display is the blurred version of
 * the same icon.
 */
public class TrayUi {

    TrayIcon icon;
    SystemTray tray;

    public boolean install(Runnable onOpen, Runnable onRescan, Runnable onExit) {
        if (!SystemTray.isSupported()) {
            System.out.println("[daemon] no system tray on this platform; running headless-ish");
            return false;
        }
        try {
            tray = SystemTray.getSystemTray();

            var menu = new PopupMenu();
            var open = new MenuItem("Open project picker");
            open.addActionListener(e -> onOpen.run());
            var rescan = new MenuItem("Rescan now");
            rescan.addActionListener(e -> onRescan.run());
            var exit = new MenuItem("Exit");
            exit.addActionListener(e -> onExit.run());

            menu.add(open);
            menu.add(rescan);
            menu.addSeparator();
            menu.add(exit);

            icon = new TrayIcon(image(), "proj - project tools", menu);
            icon.setImageAutoSize(true);
            icon.addActionListener(e -> onOpen.run());
            tray.add(icon);
            return true;
        } catch (Exception e) {
            System.err.println("[daemon] tray unavailable: " + e.getMessage());
            return false;
        }
    }

    public void note(String message) {
        if (icon != null) icon.setToolTip("proj - " + message);
    }

    public void remove() {
        if (tray != null && icon != null) tray.remove(icon);
    }

    Image image() {
        var asked = tray.getTrayIconSize();
        return AppIconArt.draw(Math.max(16, asked == null ? 16 : asked.width), AppIconArt.PROJECTS);
    }
}
