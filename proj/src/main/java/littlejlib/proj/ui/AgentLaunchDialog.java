package littlejlib.proj.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import littlejlib.proj.Settings;
import littlejlib.proj.TerminalLauncher;
import littlejlib.proj.prp.AgentExe;
import littlejlib.proj.prp.AgentLauncher;
import littlejlib.proj.prp.PrpPrompt;
import littlejlib.proj.terminal.TerminalRunner;

import java.nio.file.Path;

/**
 * Starting a coding agent on a PRP: which terminal, and in what form.
 * <p>
 * It shows the command before running it, and that is not ceremony. This starts a long-running
 * interactive process against a specification document, and the three things that can be silently
 * wrong - which PRP, which directory, and whether permissions are being skipped - are all invisible
 * the moment the terminal is up and the agent has begun reading.
 * <p>
 * THE TERMINAL IS CHOSEN PER LAUNCH rather than taken from settings. A machine with three terminals
 * installed has three of them for a reason, and the one you want for a two-hour agent session is
 * not necessarily the one you want for a quick shell. A terminal that cannot run a command is still
 * offered, doing the only thing it can: opening a shell in the right directory with the command on
 * the clipboard, ready to paste.
 * <p>
 * The third way out is the one that costs a keystroke and saves an interruption: start the agent
 * EMPTY with the prompt on the clipboard, so it can be pasted and edited before it is sent.
 */
public final class AgentLaunchDialog {

    private AgentLaunchDialog() { }

    public static void show(Stage owner, Path prpFile, Settings settings) {
        var stage = build(prpFile, settings);
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.APPLICATION_MODAL);
        }
        stage.showAndWait();
    }

    /** Built without being shown, so it can be photographed offscreen. See {@code SmokeAgentLaunch}. */
    public static Stage build(Path prpFile, Settings settings) {
        var terminals = TerminalRunner.choices(settings);
        var project = littlejlib.proj.prp.AgentLauncher.projectRootOf(prpFile);

        var stage = new Stage();
        stage.setTitle("Start a coding agent");
        AppIcon.on(stage);
        stage.setWidth(860);

        var ticket = littlejlib.proj.prp.TabName.ticketOf(prpFile,
                littlejlib.proj.prp.PrpSessions.key(project));
        var heading = new Label(ticket + "   ·   " + PrpPrompt.relative(project, prpFile));
        heading.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: #1565C0;");

        var where = new Label("Working directory: " + project
                + "   -   the project root, never the prp folder");
        where.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");

        // THE TWO THINGS WORTH DECIDING PER LAUNCH, and nothing else. The tab's name, because a row
        // of terminals all called cmd.exe is unreadable by the third one; and how much of the last
        // session's log to re-read, because that is the one number that genuinely varies by session
        // - and changing a SETTING to start one agent is a setting you forget to change back.
        // Minted ONCE, here, and used by both the preview and the launch: a second id would mean the
        // command shown is not the command run, and the recorded conversation would not exist.
        var sessionId = AgentLauncher.newSessionId();

        var tabName = new TextField(littlejlib.proj.prp.PrpSessions.tabName(prpFile));
        tabName.setPrefWidth(360);
        var tabHint = new Label("first part is this project's key - change it here and it is remembered");
        tabHint.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");
        var tabRow = new HBox(8, new Label("Tab name"), tabName, tabHint);
        tabRow.setAlignment(Pos.CENTER_LEFT);

        var logs = littlejlib.proj.prp.PrpLogs.latest(
                prpFile.toAbsolutePath().normalize().getParent(), PrpPrompt.indexOf(prpFile));
        var tail = new Spinner<Integer>(0, 100,
                settings == null ? 10 : settings.getAgentLogsTailPercent());
        tail.setEditable(true);
        tail.setPrefWidth(85);
        var tailNote = new Label(logs == null
                ? "% of the last session's log   -   this PRP has none yet, so nothing is added"
                : "% of the last session's log   -   " + logs.file().getFileName());
        tailNote.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");
        var tailRow = new HBox(8, new Label("Read"), tail, tailNote);
        tailRow.setAlignment(Pos.CENTER_LEFT);

        // Per launch, like the tab name: a monitor is Haiku, a legal draft is Opus, and a setting
        // changed for one agent is a setting forgotten afterwards. Editable, so a full model id works.
        var model = new ComboBox<String>();
        model.setEditable(true);
        model.getItems().addAll(PrpPrompt.MODELS);
        model.setPromptText("(settings default)");
        model.setPrefWidth(200);
        var modelNote = new Label("model   -   empty keeps the default of your claude settings");
        modelNote.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");
        var modelRow = new HBox(8, new Label("Model"), model, modelNote);
        modelRow.setAlignment(Pos.CENTER_LEFT);
        java.util.function.Supplier<String> modelNow = () -> model.getEditor().getText().trim();

        // Which claude: an explicit override for this launch only (fclaude to offload to the free lane),
        // never a setting. The choices are whatever *claude is on PATH.
        var exe = new ComboBox<String>();
        exe.setEditable(true);
        exe.getItems().addAll(AgentExe.candidates());
        exe.setPromptText("(claude)");
        exe.setPrefWidth(200);
        var exeNote = new Label("claude   -   empty runs the usual claude; fclaude is the free lane, pclaude is for testing");
        exeNote.setStyle("-fx-font-size: 11; -fx-text-fill: #666;");
        var exeRow = new HBox(8, new Label("Claude"), exe, exeNote);
        exeRow.setAlignment(Pos.CENTER_LEFT);
        java.util.function.Supplier<String> exeNow = () -> exe.getEditor().getText().trim();

        // What is wrong with the two choices above, or null. One answer for the preview and every action.
        java.util.function.Supplier<String> problem = () -> {
            try { PrpPrompt.modelFlag(modelNow.get()); }
            catch (IllegalArgumentException e) { return "not a model name: " + modelNow.get() + "   (haiku, sonnet, opus or a full model id)"; }
            try { AgentExe.require(exeNow.get()); }
            catch (IllegalArgumentException e) { return e.getMessage(); }
            return null;
        };
        java.util.function.BooleanSupplier modelOk = () -> problem.get() == null;

        // Read-only rather than a Label: it is long, it wraps, and being able to select part of it
        // is occasionally the fastest way to check one path in it.
        var command = new TextArea();
        command.setEditable(false);
        command.setWrapText(true);
        command.setPrefRowCount(4);
        command.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 11;");

        // Recomputed as the percentage changes, so what is shown is always what will run. A preview
        // that goes stale the moment a field is touched is worse than no preview.
        java.util.function.Supplier<String> commandNow = () -> modelOk.getAsBoolean()
                ? PrpPrompt.command(prpFile, settings, tail.getValue(), sessionId, tabName.getText(), modelNow.get(),
                        exeNow.get())
                : problem.get();
        command.setText(commandNow.get());
        tail.valueProperty().addListener((o, a, b) -> command.setText(commandNow.get()));
        tabName.textProperty().addListener((o, a, b) -> command.setText(commandNow.get()));
        model.getEditor().textProperty().addListener((o, a, b) -> command.setText(commandNow.get()));
        exe.getEditor().textProperty().addListener((o, a, b) -> command.setText(commandNow.get()));

        var permissions = new Label(settings != null && settings.isAgentSkipPermissions()
                ? "Permission prompts are OFF for this agent (" + PrpPrompt.SKIP_PERMISSIONS
                        + ") - Settings, PRP and agent"
                : "Permission prompts are on. Settings, PRP and agent, to skip them.");
        permissions.setStyle("-fx-font-size: 11; -fx-text-fill: "
                + (settings != null && settings.isAgentSkipPermissions() ? "#E65100" : "#666") + ";");

        var buttons = new VBox(5);
        var keys = new java.util.LinkedHashMap<KeyCode, Runnable>();
        var slot = 1;

        for (var t : terminals) {
            if (slot > 8) break;                       // F9..F12 are spoken for below
            var key = KeyCode.valueOf("F" + slot++);
            Runnable go = () -> {
                if (!modelOk.getAsBoolean()) { Toast.show(problem.get()); return; }
                launch(t, prpFile, project, settings, tabName.getText(), tail.getValue(), sessionId, modelNow.get(),
                        exeNow.get());
                stage.close();
            };
            keys.put(key, go);
            buttons.getChildren().add(button(label(key, t), tooltip(t), style(t), go));
        }

        Runnable pasteMode = () -> {
            if (!modelOk.getAsBoolean()) { Toast.show(problem.get()); return; }
            PrpActions.copy(PrpPrompt.of(prpFile, settings, tail.getValue()));
            var empty = PrpPrompt.commandWithoutPrompt(settings, sessionId, tabName.getText(), modelNow.get(),
                    exeNow.get());
            var name = tabName.getText();
            remember(sessionId, prpFile, project, name, exeNow.get());
            BackgroundLaunch.run(
                    () -> TerminalRunner.outcomeOf(null, project, empty, "prp-paste", settings, name),
                    outcome -> Toast.show(outcome == littlejlib.proj.terminal.RunOutcome.FAILED
                            ? "could not open a terminal - the prompt is on the clipboard"
                            : "agent started empty - the prompt is on the clipboard"));
            stage.close();
        };
        keys.put(KeyCode.F9, pasteMode);
        buttons.getChildren().add(button("F9    Start the agent empty, prompt on the clipboard",
                "Runs " + PrpPrompt.commandWithoutPrompt(settings) + " with no instruction, and puts "
                        + "the prompt on the clipboard so you can paste it and edit it before sending",
                Palette.PURPLE, pasteMode));

        Runnable copyAll = () -> {
            if (!modelOk.getAsBoolean()) { Toast.show(problem.get()); return; }
            PrpActions.copy(commandNow.get());
            Toast.show("command copied");
            stage.close();
        };
        keys.put(KeyCode.F12, copyAll);
        buttons.getChildren().add(button("F12   Copy the whole command, start nothing",
                "Puts the entire command line on the clipboard and opens no terminal",
                Palette.GREY, copyAll));

        var cancel = new Button("Esc   Cancel");
        cancel.setCancelButton(true);
        cancel.setOnAction(e -> stage.close());
        var cancelRow = new HBox(cancel);
        cancelRow.setAlignment(Pos.CENTER_RIGHT);

        var box = new VBox(9, heading, where, tabRow, tailRow, modelRow, exeRow, command, permissions, new Separator(),
                new Label("Start it in:"), buttons, cancelRow);
        box.setPadding(new Insets(16));

        Keys.noMnemonics(box);
        var scene = new Scene(box);
        // Function keys, not digits: there is a text area on this window and a digit typed into it
        // is a digit. See guidelines.keyboard-shortcuts.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isAltDown() || e.isControlDown() || e.isMetaDown()) return;
            var action = keys.get(e.getCode());
            if (action == null) return;
            e.consume();
            action.run();
        });
        stage.setScene(scene);
        stage.sizeToScene();
        return stage;
    }

    static void launch(TerminalLauncher terminal, Path prpFile, Path project, Settings settings,
                       String tabName, int percent, String sessionId, String model, String exe) {
        var command = PrpPrompt.command(prpFile, settings, percent, sessionId, tabName, model, exe);
        if (terminal.canRun()) {
            // Recorded BEFORE the launch rather than after it: the id is already in the command line
            // that is about to run, so the row is true either way, and the launch takes seconds.
            remember(sessionId, prpFile, project, tabName, exe);
            Toast.show("starting " + tabName + " in " + terminal.getName() + " ...");
            BackgroundLaunch.run(
                    () -> TerminalRunner.outcomeOf(terminal, project, command,
                            "prp-" + prpFile.getFileName(), settings, tabName),
                    outcome -> Toast.show(switch (outcome) {
                        case RAN, QUEUED -> "started on " + PrpPrompt.relative(project, prpFile)
                                + " in " + terminal.getName();
                        case OPENED_ONLY -> terminal.getName() + " opened at the project root - "
                                + outcome.note();
                        case FAILED -> "could not start " + terminal.getName() + " - check Settings";
                    }));
            return;
        }
        // It cannot be told to run something, so it is told to be somewhere, and the command is put
        // where one keystroke will produce it.
        remember(sessionId, prpFile, project, tabName, exe);
        PrpActions.copy(command);
        if (TerminalRunner.openIn(terminal, project)) {
            Toast.show(terminal.getName() + " opened at the project root - the command is on the "
                    + "clipboard, paste it");
        } else {
            Toast.show("could not open " + terminal.getName() + " - the command is on the clipboard");
        }
    }

    /**
     * The session and the name it was given, kept so this PRP can be resumed and so the name typed
     * here is the one offered next time - for this PRP, and for the next PRP of the same project.
     */
    static void remember(String sessionId, Path prpFile, Path project, String tabName, String exe) {
        littlejlib.proj.prp.PrpSessions.record(sessionId, prpFile,
                PrpPrompt.indexOf(prpFile), project, tabName, AgentExe.check(exe));
    }

    static String label(KeyCode key, TerminalLauncher t) {
        return key.getName() + "    " + (t.canRun() ? "Run in " + t.getName()
                : "Open " + t.getName() + " here, command to the clipboard");
    }

    static String tooltip(TerminalLauncher t) {
        return t.canRun()
                ? "Opens " + t.getName() + " at the project root and runs the command above"
                : t.getName() + " has no run template, so it cannot be told to run something. It "
                        + "opens at the project root instead and the command goes to the clipboard. "
                        + "Add a run template in Settings if it can in fact do this.";
    }

    static String style(TerminalLauncher t) {
        return t.canRun() ? Palette.BLUE : Palette.AMBER;
    }

    static Button button(String text, String tooltip, String style, Runnable action) {
        var b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(32);
        b.setStyle("-fx-font-size: 12; -fx-alignment: CENTER-LEFT; -fx-padding: 0 12 0 12;"
                + " -fx-background-radius: 3;" + style);
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        return b;
    }
}
