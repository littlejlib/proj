package littlejlib.proj.daemon;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * The tray presence of the resident daemon.
 * <p>
 * The icon is added once at startup and stays. mm added it in hideToSystemTray and removed it in
 * showFromSystemTray, so it only existed while the window was hidden - which is exactly backwards
 * for a process whose whole point is being permanently available.
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

    static Image image() {
        var img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(41, 98, 255));
        g.fillRoundRect(0, 0, 16, 16, 5, 5);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.drawString("p", 5, 12);
        g.dispose();
        return img;
    }
}
