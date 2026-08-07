package littlejlib.proj.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import littlejlib.proj.model.CentralStatus;
import littlejlib.proj.model.DependencyIndex;
import littlejlib.proj.model.ProjectDependency;
import littlejlib.proj.model.ProjectRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * What one project depends on, and what depends on it.
 * <p>
 * The column that makes this worth opening is Local: a dependency whose groupId:artifactId is
 * another project sitting in this index. That is the case where "is it on Maven Central" stops being
 * a fact about somebody else's release and becomes a list of things YOU have to publish first, which
 * is why an unpublished local dependency is called out as blocking rather than merely absent.
 * <p>
 * Both directions are here because the reverse one is the question you ask second and would
 * otherwise have to answer by grepping: before changing a project's coordinates or retiring it, what
 * breaks.
 */
public final class DependenciesDialog {

    private DependenciesDialog() { }

    public static void show(Stage owner, ProjectRecord project, DependencyIndex index,
                            Function<String, CentralStatus> statusOf) {

        var stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Dependencies - " + project.name());
        stage.setWidth(Math.min(1100, owner.getWidth() - 80));
        stage.setHeight(Math.min(680, owner.getHeight() - 80));

        var all = index.of(project.path());

        var localOnly = new CheckBox("_Local only");
        localOnly.setTooltip(new Tooltip("Only dependencies provided by another project in this index"));

        var blockingOnly = new CheckBox("_Not on Central");
        blockingOnly.setTooltip(new Tooltip("Only dependencies that were looked up and not found - "
                + "these are what stop this project being published"));

        var table = new TableView<Row>();
        table.setPlaceholder(new Label(all.isEmpty()
                ? "No <dependency> entries in this pom"
                : "Nothing matches these boxes"));
        VBox.setVgrow(table, Priority.ALWAYS);

        table.getColumns().add(col("Group", 250, r -> r.dep.groupId()));
        table.getColumns().add(col("Artifact", 220, r -> r.dep.artifactId()));
        table.getColumns().add(col("Version", 130, Row::version));
        table.getColumns().add(col("Scope", 80, r -> nz(r.dep.scope())));
        table.getColumns().add(col("Central", 70, Row::centralLabel));
        table.getColumns().add(col("Local", 300, Row::localLabel));

        var rows = new ArrayList<Row>();
        for (var d : all) rows.add(new Row(d, index.localProviderOf(d), statusOf.apply(d.coordinates())));
        rows.sort(java.util.Comparator.comparing((Row r) -> r.dep.ga(), String.CASE_INSENSITIVE_ORDER));

        Runnable repopulate = () -> {
            var shown = rows.stream()
                    .filter(r -> !localOnly.isSelected() || r.provider != null)
                    .filter(r -> !blockingOnly.isSelected() || r.central == CentralStatus.ABSENT)
                    .toList();
            table.getItems().setAll(shown);
        };
        localOnly.selectedProperty().addListener((o, a, b) -> repopulate.run());
        blockingOnly.selectedProperty().addListener((o, a, b) -> repopulate.run());
        repopulate.run();

        // The other direction, in the same window: before you change this project's coordinates,
        // bump its version or retire it, this is the list of what breaks. The version each dependent
        // pins is shown because that is the thing worth noticing - a project still asking for an old
        // version is one that will not pick the change up.
        var dependents = index.dependentsOf(project);
        var dependentsList = new ListView<String>();
        dependentsList.getItems().setAll(dependents.stream().map(d -> {
            var pinned = index.of(d.path()).stream()
                    .filter(x -> x.ga().equals(ProjectDependency.ga(project.groupId(), project.artifactId())))
                    .map(ProjectDependency::version).filter(v -> v != null && !v.isBlank())
                    .findFirst().orElse(null);
            var stale = pinned != null && project.version() != null && !pinned.equals(project.version());
            return d.relativePath()
                    + (pinned == null ? "" : "        depends on " + pinned)
                    + (stale ? "   (this project is at " + project.version() + ")" : "");
        }).toList());
        dependentsList.setPlaceholder(new Label("No project in this index depends on " + project.coordinates()));
        dependentsList.setPrefHeight(160);

        var summary = new Label(summarise(project, rows, dependents));
        summary.setStyle("-fx-font-size: 11px; -fx-text-fill: #444;");
        summary.setWrapText(true);

        var close = new Button("Close");
        close.setOnAction(e -> stage.close());

        var controls = new HBox(10, localOnly, blockingOnly, new Label(""), close);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(controls.getChildren().get(2), Priority.ALWAYS);

        var root = new VBox(8,
                summary,
                controls,
                new Label("Depends on"), table,
                new Label("Depended on by, within this index"), dependentsList);
        root.setPadding(new Insets(12));

        var scene = new Scene(root);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.showAndWait();
    }

    static String summarise(ProjectRecord project, List<Row> rows, List<ProjectRecord> dependents) {
        var local = rows.stream().filter(r -> r.provider != null).count();
        var absent = rows.stream().filter(r -> r.central == CentralStatus.ABSENT).count();
        var unchecked = rows.stream().filter(r -> r.central == CentralStatus.UNKNOWN
                || r.central == CentralStatus.CHECKING).count();
        var unversioned = rows.stream().filter(r -> !r.dep.versionResolved()).count();

        var sb = new StringBuilder(project.coordinates().isBlank() ? project.relativePath() : project.coordinates());
        sb.append("   -   ").append(rows.size()).append(" dependencies");
        if (local > 0) sb.append(", ").append(local).append(" from this index");
        if (absent > 0) sb.append(", ").append(absent).append(" not on Maven Central");
        if (unchecked > 0) sb.append(", ").append(unchecked).append(" not looked up yet");
        if (unversioned > 0) sb.append(", ").append(unversioned).append(" with no resolvable version");
        if (!dependents.isEmpty()) sb.append(".  ").append(dependents.size()).append(" project(s) depend on this one");
        return sb.append(".").toString();
    }

    /** One dependency, with the two facts that are not in the pom: who provides it and is it published. */
    static final class Row {
        final ProjectDependency dep;
        final ProjectRecord provider;
        final CentralStatus central;

        Row(ProjectDependency dep, ProjectRecord provider, CentralStatus central) {
            this.dep = dep;
            this.provider = provider;
            this.central = central;
        }

        String version() {
            if (!dep.versionResolved()) {
                return dep.version() == null ? "(unversioned)" : dep.version();
            }
            return dep.managed() ? dep.version() + "  (managed)" : dep.version();
        }

        String centralLabel() {
            return dep.versionResolved() ? central.glyph : "";
        }

        /**
         * Named plainly rather than glyphed: "blocks publishing" is the whole reason the local
         * column exists, and a symbol would need explaining every time.
         */
        String localLabel() {
            if (provider == null) return "";
            var where = provider.relativePath();
            return central == CentralStatus.ABSENT ? where + "   - blocks publishing" : where;
        }
    }

    static TableColumn<Row, String> col(String title, double width, Function<Row, String> get) {
        var c = new TableColumn<Row, String>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(p -> new SimpleStringProperty(nz(get.apply(p.getValue()))));
        return c;
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
