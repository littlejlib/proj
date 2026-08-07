package littlejlib.proj.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;
import littlejlib.proj.Settings;
import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.prp.PrpEntry;
import littlejlib.proj.terminal.TerminalRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The window-side of starting a coding agent on a PRP: choose which one, confirm what will run,
 * then launch.
 * <p>
 * The confirmation is not ceremony. This starts a long-running interactive process in a terminal
 * against a specification document, and the two things that can be silently wrong - which PRP, and
 * which directory - are both invisible once the terminal is open and the agent has begun reading.
 * Showing the command and the working directory once costs a keypress and makes both checkable.
 */
public final class AgentStarter {

    private AgentStarter() { }

    public static void chooseAndStart(Stage owner, Path projectDir, Settings settings) {
        var prpDir = PrpCreator.prpDirOf(projectDir);
        List<PrpEntry> prps;
        try {
            prps = Files.isDirectory(prpDir) ? PrpCreator.list(prpDir) : List.of();
        } catch (Exception e) {
            Toast.show("could not read " + prpDir + ": " + e.getMessage());
            return;
        }

        if (prps.isEmpty()) {
            Toast.show("no PRPs in " + prpDir + " - make one first (F10)");
            return;
        }

        // Newest first: the PRP you just wrote is the one you almost always mean.
        var ordered = prps.stream()
                .sorted(java.util.Comparator.comparing(PrpEntry::getIndex).reversed()).toList();

        var choice = new ChoiceDialog<>(ordered.get(0), ordered);
        choice.setTitle("Start a coding agent");
        choice.setHeaderText(projectDir.toString());
        choice.setContentText("Which PRP?");
        choice.initOwner(owner);
        // ChoiceDialog renders toString(); PrpEntry's display name is index plus name, which is
        // exactly what distinguishes them.
        var picked = choice.showAndWait().orElse(null);
        if (picked == null || picked.getMainFile() == null) return;

        start(owner, picked.getMainFile(), settings);
    }

    public static void start(Stage owner, Path prpFile, Settings settings) {
        var plan = AgentLauncher.plan(prpFile, settings);
        var terminal = TerminalRunner.resolve(settings);

        var confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Start a coding agent");
        confirm.setHeaderText("In " + terminal.getName() + ", at " + plan.projectDir());
        confirm.setContentText(plan.command()
                + "\n\nWorking directory is the project root, not the prp folder.");
        confirm.getDialogPane().setPrefWidth(700);
        if (owner != null) confirm.initOwner(owner);

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        if (AgentLauncher.launch(prpFile, settings)) {
            Toast.show("started on " + plan.relativePrp());
        } else {
            Toast.show("could not start a terminal - check Settings");
        }
    }
}
