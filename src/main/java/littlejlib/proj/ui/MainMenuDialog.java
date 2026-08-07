package littlejlib.proj.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import littlejlib.proj.Settings;

/**
 * The main menu.
 * <p>
 * FUNCTION KEYS, like every other window here. The digits 1-8 were this menu's own scheme and
 * nothing else's, so the one window a new user meets first taught a convention the rest of the
 * application does not use. There is no text field here for a letter to fall into, so the letter
 * shortcuts stay as a second way in; the labels and the footer name the function keys, because
 * those are the ones that mean the same thing in the picker and the PRP manager.
 */
public class MainMenuDialog {

    private static final Object lockObject = new Object();
    private static String dialogResult = null;

    public enum Action {
        OPEN_PROJECT,
        GIT_CLONE,
        NEW_PROJECT,
        PRPS_MANAGER,
        GLOBAL_PRPS_MANAGER,
        SETTINGS,
        IDE_EDITOR,
        BACK,
        EXIT
    }

    /**
     * Shows the main menu and returns selected action
     */
    public static Action show(Settings settings) {
        System.out.println("[MainMenuDialog] Showing main menu");
        // Long, because the thing being waited on is a person deciding. Ten seconds was enough
        // while this only ever ran in a process that was about to exit anyway; served from the
        // daemon it would report EXIT to the client with the menu still open on screen.
        String result = JavaFXManager.getInstance().showDialog(
            (lock, resultHolder) -> showOnFxThread(settings, lock, resultHolder),
            900_000
        );
        return stringToAction(result);
    }

    /**
     * Called on JavaFX thread to create and show the menu.
     * MUST NOT BLOCK - returns immediately after showing dialog.
     */
    private static void showOnFxThread(Settings settings, Object lock, Object[] resultHolder) {
        dialogResult = null;

        Stage stage = createMenuStage(settings);

        // Set up close handler to notify when dialog closes
        stage.setOnHidden(event -> {
            System.out.println("[MainMenuDialog] Dialog closing, result: " + dialogResult);
            synchronized (lock) {
                resultHolder[0] = dialogResult != null ? dialogResult : "EXIT";
                lock.notifyAll();
            }
        });

        stage.show();
        System.out.println("[MainMenuDialog] Menu shown (not blocking FX thread)");
    }

    private static Stage createMenuStage(Settings settings) {
        Stage stage = new Stage();
        stage.setTitle("Project Build & Dev Tools");
        stage.setWidth(600);
        stage.setHeight(560);
        stage.setResizable(false);

        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // Header
        Label titleLabel = new Label("Project Build & Dev Tools");
        titleLabel.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: #1976D2;");

        Label versionLabel = new Label("Version 1.0");
        versionLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #999;");

        VBox headerBox = new VBox(5);
        headerBox.getChildren().addAll(titleLabel, versionLabel);

        // Main menu buttons
        Button btnOpen = createButton("F1   Open Project", "O", "Open an existing project from your code repositories", "#1976D2", "#E3F2FD");
        Button btnClone = createButton("F2   Git Clone", "G", "Clone a GitHub repository to your code repositories path", "#F57C00", "#FFF3E0");
        Button btnNew = createButton("F3   New Project", "N", "Create a new project from a template", "#388E3C", "#E8F5E9");
        Button btnPrps = createButton("F4   PRP Manager", "P", "Manage Project Requirement Prompts (PRPs)", "#7B1FA2", "#F3E5F5");
        Button btnGlobalPrps = createButton("F5   Global PRP Viewer", "B", "Browse PRPs across all projects", "#009688", "#E0F7FA");
        Button btnSettings = createButton("F6   Settings", "S", "Configure application settings and preferences", "#0097A7", "#E0F2F1");
        Button btnIde = createButton("F7   Open in IDE", "E", "Open current directory in configured IDE", "#D32F2F", "#FFEBEE");
        Button btnExit = createButton("F8   Exit", "X", "Exit the application", "#757575", "#F5F5F5");

        btnOpen.setOnAction(e -> closeWithResult("OPEN_PROJECT", stage));
        btnClone.setOnAction(e -> closeWithResult("GIT_CLONE", stage));
        btnNew.setOnAction(e -> closeWithResult("NEW_PROJECT", stage));
        btnPrps.setOnAction(e -> closeWithResult("PRPS_MANAGER", stage));
        btnGlobalPrps.setOnAction(e -> closeWithResult("GLOBAL_PRPS_MANAGER", stage));
        btnSettings.setOnAction(e -> closeWithResult("SETTINGS", stage));
        btnIde.setOnAction(e -> closeWithResult("IDE_EDITOR", stage));
        btnExit.setOnAction(e -> closeWithResult("EXIT", stage));

        VBox buttonBox = new VBox(10);
        buttonBox.getChildren().addAll(btnOpen, btnClone, btnNew, btnPrps, btnGlobalPrps, btnSettings, btnIde, btnExit);

        // Footer
        Label footerLabel = new Label("F1-F8, or the letter in each tooltip | Esc to exit");
        footerLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #999; -fx-padding: 10 0 0 0;");

        root.getChildren().addAll(headerBox, buttonBox, footerLabel);
        root.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(root);

        // Keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case F1:
                case O:
                    closeWithResult("OPEN_PROJECT", stage);
                    event.consume();
                    break;
                case F2:
                case G:
                    closeWithResult("GIT_CLONE", stage);
                    event.consume();
                    break;
                case F3:
                case N:
                    closeWithResult("NEW_PROJECT", stage);
                    event.consume();
                    break;
                case F4:
                case P:
                    closeWithResult("PRPS_MANAGER", stage);
                    event.consume();
                    break;
                case F5:
                case B:
                    closeWithResult("GLOBAL_PRPS_MANAGER", stage);
                    event.consume();
                    break;
                case F6:
                case S:
                    closeWithResult("SETTINGS", stage);
                    event.consume();
                    break;
                case F7:
                case E:
                    closeWithResult("IDE_EDITOR", stage);
                    event.consume();
                    break;
                case F8:
                case X:
                    closeWithResult("EXIT", stage);
                    event.consume();
                    break;
                case ESCAPE:
                    closeWithResult("BACK", stage);
                    event.consume();
                    break;
                default:
                    break;
            }
        });

        stage.setScene(scene);

        // Close handler is set up in showOnFxThread() now

        return stage;
    }

    private static Button createButton(String label, String shortcut, String tooltip, String textColor, String bgColor) {
        Button btn = new Button(label);
        btn.setPrefWidth(500);
        btn.setPrefHeight(50);
        btn.setStyle(
                "-fx-font-size: 14;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + textColor + ";" +
                "-fx-background-color: " + bgColor + ";" +
                "-fx-border: 2px solid " + textColor + ";" +
                "-fx-border-radius: 3;" +
                "-fx-padding: 0;"
        );

        String tooltipText = tooltip + "   (or press " + shortcut + ")";
        btn.setTooltip(new Tooltip(tooltipText));

        return btn;
    }

    private static void closeWithResult(String action, Stage stage) {
        synchronized (lockObject) {
            dialogResult = action;
            lockObject.notifyAll();
        }
        Platform.runLater(stage::close);
    }

    private static Action stringToAction(String actionStr) {
        if (actionStr == null) {
            return Action.EXIT;
        }

        switch (actionStr) {
            case "OPEN_PROJECT":
                return Action.OPEN_PROJECT;
            case "GIT_CLONE":
                return Action.GIT_CLONE;
            case "NEW_PROJECT":
                return Action.NEW_PROJECT;
            case "PRPS_MANAGER":
                return Action.PRPS_MANAGER;
            case "GLOBAL_PRPS_MANAGER":
                return Action.GLOBAL_PRPS_MANAGER;
            case "SETTINGS":
                return Action.SETTINGS;
            case "IDE_EDITOR":
                return Action.IDE_EDITOR;
            case "BACK":
                return Action.BACK;
            case "EXIT":
            default:
                return Action.EXIT;
        }
    }
}
