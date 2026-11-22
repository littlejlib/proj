package littlejlib.proj.cli;

import littlejlib.proj.SettingsManager;
import littlejlib.proj.daemon.OpenViaDaemon;
import littlejlib.proj.ui.ProjectPickerDialog;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Paths;

@Command(name = "ui", aliases = "window", mixinStandardHelpOptions = true,
        description = "Open the picker on its Conversations tab: running ones with RAM, and hibernated ones")
public final class HibUiCmd implements Runnable {

    @Override
    public void run() {
        var code = OpenViaDaemon.open(false, "conversations");
        if (code != null) System.exit(code);
        var settings = new SettingsManager().loadSettings();
        var paths = settings.getBrowsePaths().stream().map(Paths::get).filter(Files::isDirectory).toList();
        ProjectPickerDialog.show(paths, settings, "conversations");
    }
}
