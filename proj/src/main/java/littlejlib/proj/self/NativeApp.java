package littlejlib.proj.self;

import littlejlib.proj.util.Native;

import java.io.IOException;
import java.util.Properties;

/**
 * What a jr launcher tells the app it runs (exe path, app id, version, update url and channel), for the native exe,
 * which has no launcher: the build bakes the same facts into {@code native-app.properties} and they are published here
 * as the same system properties, so {@code jr-apputils} (install, uninstall) works on the exe unchanged.
 */
public final class NativeApp {
    private NativeApp() { }

    static final String PREFIX = "io.github.jarrunner.jr.";

    public static void init() {
        if (!Native.image() || Native.exe() == null) return;
        System.setProperty(PREFIX + "exe", Native.exe().toString());
        try (var in = NativeApp.class.getResourceAsStream("/native-app.properties")) {
            if (in == null) return;
            var p = new Properties();
            p.load(in);
            p.forEach((k, v) -> System.getProperties().putIfAbsent(PREFIX + k, v.toString()));
        } catch (IOException e) {
            System.err.println("proj: " + e.getMessage());
        }
    }

    public static String version() {
        return System.getProperty(PREFIX + "app.version", "0");
    }

    public static String updateUrl() {
        return System.getProperty(PREFIX + "update.url");
    }

    public static String channel() {
        return System.getProperty(PREFIX + "update.channel", "stable");
    }
}
