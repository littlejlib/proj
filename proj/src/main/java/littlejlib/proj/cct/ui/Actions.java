package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import littlejlib.proj.cct.action.*;
import littlejlib.proj.cct.export.*;
import littlejlib.proj.cct.index.*;
import littlejlib.proj.cct.model.SessionDoc;
import littlejlib.proj.cct.search.SearchHit;
import littlejlib.proj.cct.SettingsStore;

/**
 * The keys of the transcripts tab, and the actions behind them.
 * <p>
 * F1-F12 with no modifier are the hot path; everything else sits in the F11 menu with its key
 * printed beside it. F11 is the menu here AND on the projects tab, which makes it the one key that
 * means the same thing everywhere in this window.
 */
public final class Actions {

    private final MainWindow window;
    private final DbWorker worker;
    private final ListView<SearchHit> results;
    private final SessionPane detail;
    private final ScratchPad scratch;
    private final StatusBar status;
    private final TextField query;
    private final CctHost host;
    private ActionMenu menu;

    Actions(MainWindow window, DbWorker worker, ListView<SearchHit> results, SessionPane detail,
            ScratchPad scratch, StatusBar status, TextField query, CctHost host) {
        this.window = window;
        this.worker = worker;
        this.results = results;
        this.detail = detail;
        this.scratch = scratch;
        this.status = status;
        this.query = query;
        this.host = host == null ? CctHost.standalone() : host;
    }

    public void install(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
    }

    /**
     * Called by the host when this tab has the window. A key the tab does not want is left
     * unconsumed so the host can still act on it, which is how Alt+digit keeps switching tabs.
     */
    public void onKey(KeyEvent e) {
        if (e.getCode() == KeyCode.ALT && !e.isControlDown() && !e.isShiftDown()) { e.consume(); return; }
        if (e.isAltDown() && !e.isControlDown()) { if (alt(e.getCode())) e.consume(); return; }
        if (e.isControlDown() || e.isMetaDown()) return;
        if (detail.find().holdsFocus() && !e.getCode().isFunctionKey()) return;
        if (function(e)) e.consume();
    }

    private boolean function(KeyEvent e) {
        return switch (e.getCode()) {
            case F1 -> { HelpOverlay.show(); yield true; }
            case F2 -> { detail.list().requestFocus(); yield true; }
            case F3 -> resume();
            case F4 -> give(SessionDoc::cwd, "folder path");
            case F5 -> { reindex(); yield true; }
            case F6 -> { terminal(); yield true; }
            case F7 -> { reveal(); yield true; }
            case F8 -> give(SessionDoc::sessionId, "session id");
            case F9 -> { window.cycleLayer(); yield true; }
            case F10 -> { window.cycleMode(); yield true; }
            case F11 -> { menu().show(focusedAnchor()); yield true; }
            case F12 -> { copyTurns(); yield true; }
            case ESCAPE -> back();
            case DOWN, ENTER -> { if (query.isFocused()) { results.requestFocus(); yield true; } yield false; }
            default -> false;
        };
    }

    private boolean alt(KeyCode code) {
        return switch (code) {
            case F -> { findInTranscript(); yield true; }
            case A -> { selectEveryTurn(); yield true; }
            case E -> { export(ExportFormat.MARKDOWN); yield true; }
            case X -> { export(ExportFormat.XHTML); yield true; }
            case P -> { openPrp(); yield true; }
            case J -> { activateProject(); yield true; }
            case H -> { window.cycleShell(); yield true; }
            case B -> give(ResumeCommands::block, "resume block");
            case T -> { copyTranscript(); yield true; }
            case S -> { scratch.focus(); yield true; }
            case K -> { scratch.clear(); status.say("scratch cleared"); yield true; }
            default -> false;
        };
    }

    public ActionMenu menu() {
        if (menu != null) return menu;
        var m = new ActionMenu();
        m.add("F2", "Read the turns", () -> detail.list().requestFocus());
        m.add("Alt+F", "Find in this conversation", this::findInTranscript);
        m.add("Alt+A", "Select every turn", this::selectEveryTurn);
        m.add("F12", "Copy the selected turns", this::copyTurns);
        m.add("Alt+T", "Copy the whole conversation", this::copyTranscript);
        m.separator();
        m.add("F3", "Copy the resume command", this::resume);
        m.add("Alt+B", "Copy the resume block, every shell", () -> give(ResumeCommands::block, "resume block"));
        m.add("Alt+H", "Change shell for the resume command", window::cycleShell);
        m.add("F4", "Copy the folder path", () -> give(SessionDoc::cwd, "folder path"));
        m.add("F8", "Copy the session id", () -> give(SessionDoc::sessionId, "session id"));
        m.separator();
        m.add("Alt+E", "Export as markdown", () -> export(ExportFormat.MARKDOWN));
        m.add("", "Export as plain text", () -> export(ExportFormat.TEXT));
        m.add("Alt+X", "Export as semantic xhtml", () -> export(ExportFormat.XHTML));
        m.add("", "Export the raw jsonl transcript", () -> export(ExportFormat.JSONL));
        m.separator();
        m.add("Alt+P", "Open the PRP file this session opened with", this::openPrp,
            () -> PrpLink.of(selected()) != null);
        m.add("F7", "Reveal the folder in Explorer", this::reveal);
        m.add("F6", "Open a terminal there", this::terminal);
        m.add("Alt+J", "Show this project in the Projects tab", this::activateProject,
            () -> host.canActivateProject() && PrpLink.projectDir(selected()) != null);
        m.separator();
        m.add("F9", "Widen the scope", window::cycleLayer);
        m.add("F10", "Change the match mode", window::cycleMode);
        m.add("F5", "Reindex", this::reindex);
        m.add("F1", "Every key", HelpOverlay::show);
        menu = m;
        return m;
    }

    javafx.scene.Node focusedAnchor() {
        if (detail.list().isFocused()) return detail.list();
        if (results.isFocused()) return results;
        return query;
    }

    private void selectEveryTurn() {
        detail.selectAll();
        status.say(detail.selectedTurns().size() + " turns selected - F12 copies them, Alt+E exports");
    }

    private void findInTranscript() {
        if (detail.current() == null) { status.say("open a session first - F2 reads its turns"); return; }
        detail.find().show();
    }

    private boolean resume() {
        var shell = window.shell();
        return give(s -> ResumeCommands.in(shell, s, false, false), "resume command for " + shell.label);
    }

    /**
     * One step back each press, and false once there is nowhere left to go - which is what lets Esc
     * fall through to the host and leave the tab instead of being a key that does nothing forever.
     */
    private boolean back() {
        if (detail.find().visible()) {
            detail.find().hide();
            detail.list().requestFocus();
            return true;
        }
        if (scratch.area().isFocused() || detail.list().isFocused()) {
            results.requestFocus();
            return true;
        }
        if (!query.getText().isEmpty()) { query.clear(); query.requestFocus(); return true; }
        if (!query.isFocused()) { query.requestFocus(); return true; }
        return false;
    }

    private boolean give(Function<SessionDoc, String> pick, String what) {
        var s = selected();
        if (s == null) { status.say("nothing selected"); return true; }
        var value = pick.apply(s);
        if (value == null || value.isBlank()) { status.say("no " + what + " for this session"); return true; }
        Shell.copy(value);
        scratch.append(value);
        status.say(what + " copied to clipboard and added to scratch");
        return true;
    }

    private void copyTurns() {
        var picked = detail.selectedTurns();
        var text = detail.selectedTurnText();
        if (text == null || text.isBlank()) {
            status.say("no turn selected - press F2, then Alt+A for all of them");
            return;
        }
        Shell.copy(text);
        scratch.append(text);
        status.say(picked.size() == 1
            ? "that turn copied to clipboard and added to scratch"
            : picked.size() + " turns copied to clipboard and added to scratch");
    }

    private void copyTranscript() {
        var text = detail.transcriptText();
        if (text == null || text.isBlank()) { status.say("no session open"); return; }
        Shell.copy(text);
        status.say("whole conversation copied to clipboard (" + text.length()
            + " chars, not added to scratch)");
    }

    private void export(ExportFormat format) {
        var s = detail.current() != null ? detail.current() : selected();
        if (s == null) { status.say("nothing selected"); return; }
        var open = detail.current() == s && !detail.list().getItems().isEmpty()
            ? new ArrayList<>(detail.list().getItems()) : null;
        var svc = worker.search();
        var layer = Math.max(2, window.layer());
        status.say("exporting as " + format.name().toLowerCase(Locale.ROOT) + "…");
        worker.run(() -> {
            try {
                var body = open != null ? open : svc.turns(s.sessionId(), layer);
                return TranscriptExport.writeToExportDir(s, body, format).toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, path -> {
            Shell.copy(path);
            scratch.append(path);
            status.say("written to " + path + "   (path copied)");
        }, status::say);
    }

    private void openPrp() {
        var prp = PrpLink.of(selected());
        if (prp == null) { status.say("no PRP file named in this session's first prompt"); return; }
        host.openInEditor(prp);
        status.say("opening " + prp);
    }

    private void activateProject() {
        var dir = PrpLink.projectDir(selected());
        if (dir == null) { status.say("no folder recorded for this session, or it no longer exists"); return; }
        if (!host.canActivateProject()) {
            status.say("the projects tab is only there when this runs inside proj");
            return;
        }
        host.activateProject(dir);
    }

    private void terminal() {
        var s = selected();
        if (s == null || s.cwd() == null) { status.say("no folder recorded for this session"); return; }
        var ok = host.openTerminal(java.nio.file.Path.of(s.cwd()));
        status.say(ok ? "terminal opened in " + s.cwd() : "could not open a terminal there");
    }

    private void reveal() {
        var s = selected();
        if (s == null || s.cwd() == null) { status.say("no folder recorded for this session"); return; }
        status.say(Shell.reveal(s.cwd()) ? "opened in Explorer" : "that folder no longer exists");
    }

    private void reindex() {
        status.say("reindexing…");
        var db = worker.database();
        if (db == null) { status.say("index not open"); return; }
        var opts = IndexOptions.defaults()
            .includeAssistant(SettingsStore.get().includeAssistant())
            .includeThinking(SettingsStore.get().includeThinking());
        worker.run(() -> Indexer.run(db, opts, m -> { }),
            report -> { status.say(report.summary()); window.refresh(); },
            status::say);
    }

    SessionDoc selected() {
        if (detail.current() != null && detail.list().isFocused()) return detail.current();
        var hit = results.getSelectionModel().getSelectedItem();
        return hit == null ? detail.current() : hit.session;
    }
}
