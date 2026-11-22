package littlejlib.proj.cct;

import module java.base;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import littlejlib.proj.cct.store.StorePaths;

public final class SettingsStore {

    private SettingsStore() {}

    private static final TomlMapper TOML = new TomlMapper();
    private static Settings cached;

    public static synchronized Settings get() {
        if (cached == null) cached = load();
        return cached;
    }

    private static Settings load() {
        var file = StorePaths.settingsFile();
        if (!Files.isRegularFile(file)) return Settings.defaults();
        try {
            return TOML.readValue(file.toFile(), Settings.class);
        } catch (IOException e) {
            return Settings.defaults();
        }
    }

    public static synchronized void save(Settings s) {
        cached = s;
        try {
            StorePaths.ensureAppHome();
            TOML.writerWithDefaultPrettyPrinter().writeValue(StorePaths.settingsFile().toFile(), s);
        } catch (IOException e) {
            // settings are a convenience; failing to persist must not break the app
        }
    }
}
