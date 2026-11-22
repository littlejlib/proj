package littlejlib.proj.cct.ui;

import module java.base;

import littlejlib.proj.cct.SettingsStore;

/**
 * What a lone transcripts window can do: open a file in the house editor, which is on PATH, and
 * admit it cannot reach proj's project table. Used by the standalone app, which exists for testing.
 */
public final class StandaloneHost implements CctHost {

    @Override
    public void openInEditor(Path file) {
        if (file == null) return;
        var path = file.toAbsolutePath().toString();
        if (launch("code.exe", path)) return;
        if (launch("code", path)) return;
        littlejlib.proj.util.OpenFile.open(file);
    }

    @Override
    public String terminalCommand() { return SettingsStore.get().terminalCommand(); }

    static boolean launch(String exe, String arg) {
        try {
            new ProcessBuilder(exe, arg).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
