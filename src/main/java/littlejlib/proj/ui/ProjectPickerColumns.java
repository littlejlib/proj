package littlejlib.proj.ui;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import littlejlib.proj.IdeLauncher;
import littlejlib.proj.Settings;
import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.ide.DetectedIde;
import littlejlib.proj.ide.IdeDetector;
import littlejlib.proj.model.CentralStatus;
import littlejlib.proj.model.ProjectConcerns;
import littlejlib.proj.model.ProjectRecord;
import littlejlib.proj.util.Cmd;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
 * Sorting a concern needs the concern to BE a value, so those columns carry the record itself and a
 * comparator, rather than a rendered string that would sort alphabetically by its glyph.
 * <p>
 * The action buttons live in one Actions cell rather than a column each. Git details and Description
 * are present but hidden by default and can be switched on from the table's own menu button (top
 * right of the header) - JavaFX has no frozen-column support, so the only real cure for a table
 * wider than the screen is fewer columns in it.
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
        var cols = table.getColumns();

        var num = new TreeTableColumn<ProjectRecord, String>("#");
        num.setPrefWidth(46);
        num.setSortable(false);
        num.setCellValueFactory(p -> new SimpleStringProperty(""));
        num.setCellFactory(c -> new RowNumberCell());
        cols.add(num);

        // The relative path, not just the leaf name: without it, sorted siblings from different
        // parents read as an arbitrary jumble of short names.
        var project = text("Project", 320, r -> r.isCodeDirNode() ? r.name() : r.relativePath());
        cols.add(project);
        table.setTreeColumn(project);

        cols.add(text("Ver", 74, ProjectRecord::version));

        cols.add(record("Build", 66, ProjectPickerColumns::buildLabel, ProjectPickerColumns::buildTooltip,
                Comparator.comparing(ProjectPickerColumns::buildLabel)));

        cols.add(record("Git", 82, r -> r.gitState().label, ProjectPickerColumns::gitTooltip,
                Comparator.comparingInt(r -> r.gitState().ordinal())));

        var central = record("Central", 68,
                r -> centralStatus(r, checker).glyph,
                r -> centralTooltip(r, checker),
                Comparator.comparingInt(r -> centralSeverity(centralStatus(r, checker))));
        // The lookup is triggered from the cell, which is what makes it lazy for free: a
        // TreeTableView only builds cells for rows near the viewport, so scrolling widens the query
        // set and nothing is fetched for the hundreds of rows nobody scrolled to. The whole-set
        // sweep exists alongside this for the questions that are asked of every row at once.
        central.setCellFactory(c -> new CentralCell(checker, repaint));
        cols.add(central);

        cols.add(record("Deps", 200,
                r -> label(concernsOf.apply(r), ProjectConcerns::depsLabel),
                r -> depsTooltip(concernsOf.apply(r)),
                Comparator.comparingInt((ProjectRecord r) -> {
                    var c = concernsOf.apply(r);
                    return c == null ? -1 : c.severity();
                }).reversed()));

        var actions = new TreeTableColumn<ProjectRecord, String>("Actions");
        // Wider than before: each button now carries its function key as well as its name.
        actions.setPrefWidth(BAR.size() * 60 + 12);
        actions.setSortable(false);
        actions.setCellValueFactory(p -> new SimpleStringProperty(""));
        actions.setCellFactory(c -> new ActionBarCell(BAR, onAction));
        cols.add(actions);

        // One column, one icon per installed IDE, taken from the executable itself. Anything the
        // user configured by hand wins; otherwise this is whatever is actually on the machine.
        var configured = settings.getIdeLaunchers().stream().filter(IdeLauncher::isUsable).toList();
        var ides = configured.isEmpty()
                ? IdeDetector.detect().stream().filter(i -> i.opensFolders).toList()
                : configured.stream()
                        .map(l -> new DetectedIde(l.getName(), firstToken(l.getCommand()), l.getCommand(), true))
                        .toList();

        if (!ides.isEmpty()) {
            var ideCol = new TreeTableColumn<ProjectRecord, String>("Open in");
            ideCol.setPrefWidth(Math.max(70, ides.size() * 30 + 14));
            ideCol.setSortable(false);
            ideCol.setCellValueFactory(p -> new SimpleStringProperty(""));
            ideCol.setCellFactory(c -> new IdeBarCell(ides, onLaunch));
            cols.add(ideCol);
        }

        var description = text("Description", 240, ProjectRecord::description);
        description.setVisible(false);
        cols.add(description);

        var git = new TreeTableColumn<ProjectRecord, String>("Git details");
        git.getColumns().add(text("User", 100, ProjectRecord::gitUser));
        git.getColumns().add(text("Org", 110, ProjectRecord::gitOrg));
        git.getColumns().add(text("Repo", 130, ProjectRecord::gitRepo));
        git.getColumns().add(text("Remote", 240, ProjectRecord::gitRemote));
        git.setVisible(false);
        cols.add(git);
    }

    // ---------- rendering ----------

    static String buildLabel(ProjectRecord r) {
        if (r.isCodeDirNode()) return "";
        var sb = new StringBuilder();
        if (r.maven()) sb.append("mvn ");
        if (r.gradle()) sb.append("gr ");
        if (r.jbang()) sb.append("jb ");
        return sb.toString().trim();
    }

    static String buildTooltip(ProjectRecord r) {
        var label = buildLabel(r);
        return label.isEmpty()
                ? "no build file - a plain directory or a bare git checkout"
                : "maven=" + r.maven() + "  gradle=" + r.gradle() + "  jbang=" + r.jbang();
    }

    static String gitTooltip(ProjectRecord r) {
        var state = r.gitState();
        var remote = r.gitRemote();
        return remote == null || remote.isBlank() ? state.description : state.description + "\n" + remote;
    }

    static CentralStatus centralStatus(ProjectRecord r, CentralChecker checker) {
        if (r.isCodeDirNode() || !r.maven()) return CentralStatus.NOT_MAVEN;
        return checker.statusOf(r.coordinates());
    }

    static String centralTooltip(ProjectRecord r, CentralChecker checker) {
        var coords = r.coordinates();
        return switch (centralStatus(r, checker)) {
            case PUBLISHED -> coords + " is on Maven Central";
            case ABSENT -> coords + " was not found on Maven Central";
            case CHECKING -> "checking " + coords;
            case UNKNOWN -> "not looked up yet";
            case NOT_MAVEN -> "";
        };
    }

    /** Absent first: the point of sorting this column is to find what is NOT published. */
    static int centralSeverity(CentralStatus status) {
        return switch (status) {
            case ABSENT -> 0;
            case UNKNOWN -> 1;
            case CHECKING -> 2;
            case PUBLISHED -> 3;
            case NOT_MAVEN -> 4;
        };
    }

    static String depsTooltip(ProjectConcerns c) {
        if (c == null || c.dependencyCount() == 0) return "";
        var sb = new StringBuilder(c.dependencyCount() + " declared dependencies");
        if (!c.localDependencies().isEmpty()) {
            sb.append("\n\nLocal - provided by another project in this index:");
            c.localDependencies().forEach(d -> sb.append("\n   ").append(d.ga()));
        }
        if (!c.absentDependencies().isEmpty()) {
            sb.append("\n\nNot on Maven Central - this is what stops THIS project being published:");
            c.absentDependencies().forEach(d -> sb.append("\n   ").append(d.coordinates()));
        }
        if (c.uncheckedDependencies() > 0) {
            sb.append("\n\n").append(c.uncheckedDependencies()).append(" not looked up yet - the counts above are provisional");
        }
        if (c.unresolvedDependencies() > 0) {
            sb.append("\n").append(c.unresolvedDependencies())
              .append(" with no resolvable version, so they cannot be checked at all");
        }
        return sb.toString();
    }

    static String label(ProjectConcerns c, Function<ProjectConcerns, String> get) {
        return c == null ? "" : get.apply(c);
    }

    // ---------- column factories ----------

    /** The executable out of a configured command template, so its icon can still be shown. */
    static Path firstToken(String command) {
        var parts = Cmd.parse(command);
        return parts.length == 0 ? null : Path.of(parts[0]);
    }

    static TreeTableColumn<ProjectRecord, String> text(String title, double width, Function<ProjectRecord, String> get) {
        var col = new TreeTableColumn<ProjectRecord, String>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(p -> {
            var rec = p.getValue() == null ? null : p.getValue().getValue();
            var v = rec == null ? "" : get.apply(rec);
            return new SimpleStringProperty(v == null ? "" : v);
        });
        return col;
    }

    /**
     * A column whose value is the record, so its sort order can be anything about the project rather
     * than the alphabetical order of whatever the cell happens to display.
     */
    static TreeTableColumn<ProjectRecord, ProjectRecord> record(
            String title, double width, Function<ProjectRecord, String> render,
            Function<ProjectRecord, String> tooltip, Comparator<ProjectRecord> order) {

        var col = new TreeTableColumn<ProjectRecord, ProjectRecord>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(p -> new SimpleObjectProperty<>(
                p.getValue() == null ? null : p.getValue().getValue()));
        col.setComparator(nullsLast(order));
        col.setCellFactory(c -> new TreeTableCell<>() {
            @Override
            protected void updateItem(ProjectRecord rec, boolean empty) {
                super.updateItem(rec, empty);
                if (empty || rec == null || rec.isCodeDirNode()) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(render.apply(rec));
                var tip = tooltip.apply(rec);
                setTooltip(tip == null || tip.isBlank() ? null : new Tooltip(tip));
            }
        });
        return col;
    }

    /** The synthetic code-directory rows carry no value at all; they must not lead the sort. */
    static Comparator<ProjectRecord> nullsLast(Comparator<ProjectRecord> order) {
        return (a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return order.compare(a, b);
        };
    }
}
