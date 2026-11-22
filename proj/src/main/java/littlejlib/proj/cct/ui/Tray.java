package littlejlib.proj.cct.ui;

import module java.base;
import module java.desktop;

public final class Tray {

    private java.awt.TrayIcon icon;

    public boolean install(Runnable onShow, Runnable onQuit) {
        if (!java.awt.SystemTray.isSupported()) return false;
        var image = awtImage();
        if (image == null) return false;
        var menu = new java.awt.PopupMenu();
        var show = new java.awt.MenuItem("Show transcripts");
        var quit = new java.awt.MenuItem("Quit");
        show.addActionListener(e -> onShow.run());
        quit.addActionListener(e -> onQuit.run());
        menu.add(show);
        menu.addSeparator();
        menu.add(quit);
        icon = new java.awt.TrayIcon(image, "transcripts — search past Claude Code conversations", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> onShow.run());
        try {
            java.awt.SystemTray.getSystemTray().add(icon);
            return true;
        } catch (java.awt.AWTException e) {
            icon = null;
            return false;
        }
    }

    public void note(String title, String body) {
        if (icon != null) icon.displayMessage(title, body, java.awt.TrayIcon.MessageType.NONE);
    }

    public void remove() {
        if (icon == null) return;
        java.awt.SystemTray.getSystemTray().remove(icon);
        icon = null;
    }

    private static java.awt.Image awtImage() {
        try (var in = AppIcon.stream(32)) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }
}
