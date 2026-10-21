package littlejlib.proj.ui;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;
import littlejlib.proj.Settings;

import java.util.function.Consumer;

/**
 * Carrying out a {@link PrpAction}, in the one place all three PRP views call.
 * <p>
 * The popup, the side panel and the global tab used to be three copies of a switch statement, which
 * is how the {@code @prp/} path came to be computed two different ways - one of them wrong. See
 * {@link #claudePath}.
 */
public final class PrpActions {

    private PrpActions() { }

    /**
     * @param say where a one-line confirmation goes - the panel's own status line, which is not the
     *            same label in each of the three places this runs
     */
    public static void perform(PrpAction action, PrpNode node, Settings settings,
                               Stage owner, Consumer<String> say) {
        if (node == null || !action.supportedBy(node)) return;
        var file = node.target();

        switch (action) {
            case OPEN -> {
                if (file == null) say.accept("that file does not exist yet - F3 creates the status file");
                else {
                    PrpManagerActions.openInEditor(file, settings);
                    say.accept("opening " + file.getFileName());
                }
            }
            case STATUS -> {
                PrpManagerActions.openStatusFile(node.prp.getPrpEntry(), settings, node.prp.getPrpDir());
                say.accept("status file: " + node.prp.getIndex() + "-prp.status.md");
            }
            case COPY_PATH -> {
                if (file == null) say.accept("that file does not exist yet, so it has no path to copy");
                else {
                    copy(file.toAbsolutePath().toString());
                    say.accept("copied path: " + file.getFileName());
                }
            }
            case COPY_CLAUDE -> {
                if (file == null) say.accept("that file does not exist yet, so it has no path to copy");
                else {
                    var text = claudePath(node);
                    copy(text);
                    say.accept("copied " + text);
                }
            }
            case FOLDER -> {
                PrpManagerActions.openFolder(node.prp.getPrpDir());
                say.accept("opening " + node.prp.getPrpDir());
            }
            case TOGGLE -> {
                PrpManagerActions.togglePrpStatus(node.prp.getPrpEntry());
                say.accept("toggled " + node.prp.getIndex() + " - refreshing");
            }
            case AGENT -> AgentStarter.start(owner, node.prp.getMainFile(), settings);
            case RESUME -> resume(node, settings, say);
        }
    }

    /**
     * Straight back in, with no dialog: the terminal, the directory and the tab name are all already
     * decided by the launch this is resuming. Only sessions proj started are here - it knows their
     * ids because it minted them - so the honest answer for the rest is the Transcripts tab.
     */
    static void resume(PrpNode node, Settings settings, Consumer<String> say) {
        var prpFile = node.prp.getMainFile();
        var sessions = littlejlib.proj.prp.PrpSessions.of(prpFile);
        if (sessions.isEmpty()) {
            say.accept("no conversation recorded for PRP " + node.prp.getIndex()
                    + " - the Transcripts tab searches the ones proj did not start");
            return;
        }
        var session = sessions.get(0);
        var plan = littlejlib.proj.prp.PrpResume.plan(prpFile, session, settings, false);
        var more = sessions.size() > 1 ? "   (" + (sessions.size() - 1) + " older)" : "";
        say.accept("resuming " + session.tabName() + more + " ...");
        BackgroundLaunch.run(() -> littlejlib.proj.prp.PrpResume.launch(plan, settings, null),
                outcome -> say.accept(switch (outcome) {
                    case RAN, QUEUED -> "resumed " + session.tabName() + more;
                    case OPENED_ONLY -> outcome.note();
                    case FAILED -> "could not open a terminal - check Settings";
                }));
    }

    /**
     * {@code @prp/27-prp-name.md}, relative to the PRP's OWN project root.
     * <p>
     * NOT relative to the process working directory, which is what {@code PrpManagerActions} does
     * and which is only ever right by coincidence: the daemon's working directory is wherever it
     * was started, and the side panel routinely shows a project that is not it. Getting this wrong
     * produces a path that looks plausible and that the agent cannot open.
     */
    public static String claudePath(PrpNode node) {
        var file = node.target();
        var project = node.prp.getProjectPath();
        try {
            return "@" + project.toAbsolutePath().normalize()
                    .relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.toAbsolutePath().toString();
        }
    }

    public static void copy(String text) {
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** True when the action changed something on disk that the list has to be reloaded to see. */
    public static boolean mutatesList(PrpAction action) {
        return action == PrpAction.TOGGLE;
    }
}
