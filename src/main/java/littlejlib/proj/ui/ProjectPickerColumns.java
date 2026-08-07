package littlejlib.proj.ui;

import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import littlejlib.proj.IdeLauncher;
import littlejlib.proj.Settings;
import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.ide.DetectedIde;
import littlejlib.proj.ide.IdeDetector;
import littlejlib.proj.model.ProjectConcerns;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.util.Cmd;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static littlejlib.proj.ui.PickerColumnFactory.*;
import static littlejlib.proj.ui.PickerColumnRender.*;

/**
 * The picker's columns: mm's information, proj's terminal-cd, minus the symbols export, plus the
 * concern columns - build system, git state, Maven Central and dependency health.
 * <p>
 * Every informational column is sortable from its header, which is the cheapest possible way to ask
 * "which of these are a problem": one click on Git brings the projects that were never pushed to the
 * top, one click on Deps brings the ones that cannot be released. The columns that are not
 * information - the row number, the button bars - stay unsortable, because sorting a column of
 * buttons means nothing.
 * <p>
 * The action buttons live in one Actions cell rather than a column each. Artifact, Description and
 * Git details are present but hidden by default, and can be switched on from the table's own menu
 * button (top right of the header) or from Settings - JavaFX has no frozen-column support, so the
 * only real cure for a table wider than the screen is fewer columns in it.
 * <p>
 * This class says WHICH columns exist. {@link PickerColumnFactory} builds one,
 * {@link PickerColumnRender} decides what it says, and {@link PickerColumnLayout} decides which are
 * shown and in what order - remembered between runs.
 */
public final class ProjectPickerColumns {

    private ProjectPickerColumns() { }

    static final List<ProjectAction> BAR = List.of(
            ProjectAction.TERMINAL_CD, ProjectAction.OPEN_FOLDER, ProjectAction.OPEN_CONSOLE,
            ProjectAction.EDIT_POM, ProjectAction.COPY_PATH, ProjectAction.COPY_DEPENDENCY,
            ProjectAction.COPY_PROJECT, ProjectAction.DEPENDENCIES, ProjectAction.PRP);

    public static void install(TreeTableView<ProjectRecord> table, Settings settings, CentralChecker checker,
                               Function<ProjectRecord, ProjectConcerns> concernsOf,
                               Runnable repaint, BiConsumer<ProjectAction, ProjectRecord> onAction,
                               BiConsumer<DetectedIde, ProjectRecord> onLaunch) {

        // Built into a map first and added to the table afterwards, because the order they go in is
        // the user's remembered order rather than the order they are written here.
        var built = new EnumMap<PickerColumn, TreeTableColumn<ProjectRecord, ?>>(PickerColumn.class);

        var num = widgets(PickerColumn.NUM, 46);
        num.setCellFactory(c -> new RowNumberCell());
        built.put(PickerColumn.NUM, num);

        // The relative path, not just the leaf name: without it, sorted siblings from different
        // parents read as an arbitrary jumble of short names. The leaf name gets its own column
        // beside it, because that is the half you actually read once you know where you are - and
        // sorting by it groups every "cli" or "core" across the tree, which the path cannot do.
        built.put(PickerColumn.PROJECT,
                text(PickerColumn.PROJECT, 300, r -> r.isCodeDirNode() ? r.name() : r.relativePath()));

        built.put(PickerColumn.NAME,
                text(PickerColumn.NAME, 190, ProjectRecord::name, PickerColumnRender::nameTooltip));

        // Also on hover from Name, so the coordinates are reachable without turning this one on.
        built.put(PickerColumn.ARTIFACT,
                text(PickerColumn.ARTIFACT, 190, ProjectRecord::artifactId, ProjectRecord::coordinates));

        built.put(PickerColumn.VERSION, text(PickerColumn.VERSION, 74, ProjectRecord::version));

        built.put(PickerColumn.BUILD, record(PickerColumn.BUILD, 66,
                PickerColumnRender::buildLabel, PickerColumnRender::buildTooltip,
                Comparator.comparing(PickerColumnRender::buildLabel)));

        built.put(PickerColumn.GIT, record(PickerColumn.GIT, 82,
                r -> r.gitState().label, PickerColumnRender::gitTooltip,
                Comparator.comparingInt(r -> r.gitState().ordinal())));

        var central = record(PickerColumn.CENTRAL, 68,
                r -> centralStatus(r, checker).glyph,
                r -> centralTooltip(r, checker),
                Comparator.comparingInt(r -> centralSeverity(centralStatus(r, checker))));
        // The lookup is triggered from the cell, which is what makes it lazy for free: a
        // TreeTableView only builds cells for rows near the viewport, so scrolling widens the query
        // set and nothing is fetched for the hundreds of rows nobody scrolled to. The whole-set
        // sweep exists alongside this for the questions that are asked of every row at once.
        central.setCellFactory(c -> new CentralCell(checker, repaint));
        built.put(PickerColumn.CENTRAL, central);

        built.put(PickerColumn.DEPS, record(PickerColumn.DEPS, 200,
                r -> label(concernsOf.apply(r), ProjectConcerns::depsLabel),
                r -> depsTooltip(concernsOf.apply(r)),
                Comparator.comparingInt((ProjectRecord r) -> {
                    var c = concernsOf.apply(r);
                    return c == null ? -1 : c.severity();
                }).reversed()));

        // Wide, because each button carries its function key as well as its name.
        var actions = widgets(PickerColumn.ACTIONS, BAR.size() * 60 + 12);
        actions.setCellFactory(c -> new ActionBarCell(BAR, onAction));
        built.put(PickerColumn.ACTIONS, actions);

        ideColumn(settings, onLaunch).ifPresent(col -> built.put(PickerColumn.IDES, col));

        built.put(PickerColumn.DESCRIPTION, text(PickerColumn.DESCRIPTION, 240, ProjectRecord::description));
        built.put(PickerColumn.GIT_DETAILS, gitDetailsColumn());

        PickerColumnLayout.apply(table, settings, built);
        PickerColumnLayout.remember(table, settings, built);
    }

    /**
     * One column, one icon per installed IDE, taken from the executable itself. Anything the user
     * configured by hand wins; otherwise this is whatever is actually on the machine - and on a
     * machine with neither, there is no column rather than an empty one.
     */
    static Optional<TreeTableColumn<ProjectRecord, String>> ideColumn(
            Settings settings, BiConsumer<DetectedIde, ProjectRecord> onLaunch) {

        var configured = settings.getIdeLaunchers().stream().filter(IdeLauncher::isUsable).toList();
        var ides = configured.isEmpty()
                ? IdeDetector.detect().stream().filter(i -> i.opensFolders).toList()
                : configured.stream()
                        .map(l -> new DetectedIde(l.getName(), firstToken(l.getCommand()), l.getCommand(), true))
                        .toList();
        if (ides.isEmpty()) return Optional.empty();

        var col = widgets(PickerColumn.IDES, Math.max(70, ides.size() * 30 + 14));
        col.setCellFactory(c -> new IdeBarCell(ides, onLaunch));
        return Optional.of(col);
    }

    static TreeTableColumn<ProjectRecord, String> gitDetailsColumn() {
        var git = new TreeTableColumn<ProjectRecord, String>(PickerColumn.GIT_DETAILS.title);
        git.setId(PickerColumn.GIT_DETAILS.id);
        git.getColumns().add(text("User", 100, ProjectRecord::gitUser));
        git.getColumns().add(text("Org", 110, ProjectRecord::gitOrg));
        git.getColumns().add(text("Repo", 130, ProjectRecord::gitRepo));
        git.getColumns().add(text("Remote", 240, ProjectRecord::gitRemote));
        return git;
    }

    /** The executable out of a configured command template, so its icon can still be shown. */
    static Path firstToken(String command) {
        var parts = Cmd.parse(command);
        return parts.length == 0 ? null : Path.of(parts[0]);
    }
}
