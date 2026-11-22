package littlejlib.proj.cli;

import littlejlib.proj.track.Item;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** {@code proj prp plan}: the project's work items (prp/workplan.jsonl). */
@Command(name = "plan", mixinStandardHelpOptions = true,
        description = "The project's work items: todo, discuss, confirm (prp/workplan.jsonl)",
        subcommands = {PlanAddCmd.class, PlanSetCmd.class, PlanListCmd.class, PlanShowCmd.class, PlanCheckCmd.class, PlanImportCmd.class, PlanReportCmd.class})
public class PlanCli implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** "3,4" and "3 4" and repeated options all give [3, 4]. */
    static List<Long> ids(List<String> typed) {
        if (typed == null) return null;
        var out = new ArrayList<Long>();
        for (var t : typed) for (var part : t.split("[,\\s]+")) if (!part.isBlank()) out.add(Long.parseLong(part.strip()));
        return out.isEmpty() ? null : out;
    }

    static List<String> uuids(Path prpDir, List<String> typed) {
        if (typed == null) return null;
        var out = new ArrayList<String>();
        for (var t : typed) for (var part : t.split("[,\\s]+")) if (!part.isBlank()) out.add(TrackCli.target(prpDir, part));
        return out.isEmpty() ? null : out;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Refuses to start an item (doing) while live items hold up to capacity a resource it uses. */
    static void refuseHeld(Path prpDir, Item was, Item next) throws java.io.IOException {
        if (!"doing".equals(next.status()) || "doing".equals(was.status()) || next.uses() == null || next.uses().isEmpty()) return;
        var project = prpDir.getParent();
        var pj = littlejlib.proj.prp.ProjFile.read(project);
        var name = pj != null && pj.name() != null ? pj.name() : project.getFileName().toString();
        var projects = new java.util.LinkedHashMap<String, Path>();
        projects.put(name, project);
        for (var p : littlejlib.proj.track.ProjectScope.known())
            if (p.hasWorkplan() && !p.dir().equals(project)) projects.putIfAbsent(p.name(), p.dir());
        var held = littlejlib.proj.track.Claims.conflicts(name, next, projects);
        if (!held.isEmpty()) throw new IllegalArgumentException(next.id() + " not started: " + String.join("; ", held)
                + ". Stop and report, or use --force to start anyway.");
    }

    /** The short name of the project owning {@code prpDir}. */
    static String projectName(Path prpDir) {
        var pj = littlejlib.proj.prp.ProjFile.read(prpDir.getParent());
        return pj != null && pj.name() != null ? pj.name() : prpDir.getParent().getFileName().toString();
    }

    /**
     * {@code billing#5} or {@code billing:5} in another project: a checked link (that item exists); null for a plain id
     * or for this project's own name.
     */
    static littlejlib.proj.track.Link link(Path prpDir, String typed) {
        var ref = littlejlib.proj.report.ReportQuery.itemRef(typed);
        if (ref == null) return null;
        try {
            var p = littlejlib.proj.track.ProjectScope.resolve(ref[0]);
            if (p.dir().toAbsolutePath().normalize().equals(prpDir.getParent().toAbsolutePath().normalize())) return null;
            var id = Long.parseLong(ref[1]);
            if (!littlejlib.proj.track.Workplan.current(p.dir().resolve("prp")).containsKey(id))
                throw new IllegalArgumentException("no item " + id + " in " + p.name() + "'s work plan");
            return new littlejlib.proj.track.Link().project(p.name()).uuid(p.uuid()).id(id);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** {@code --blocks billing#5}: that item now waits for this one; written into ITS project's plan, under its lock. */
    static void blocks(Path prpDir, long thisId, List<String> typed, String session) throws java.io.IOException {
        if (typed == null) return;
        var pj = littlejlib.proj.prp.ProjFile.read(prpDir.getParent());
        var me = new littlejlib.proj.track.Link().project(projectName(prpDir)).uuid(pj == null ? null : pj.uuid()).id(thisId);
        for (var t : typed) for (var part : t.split("[,\\s]+")) {
            if (part.isBlank()) continue;
            var target = link(prpDir, part.strip());
            if (target == null) {
                var id = Long.parseLong(part.strip().replaceAll("^.*[#:]", ""));
                littlejlib.proj.track.Workplan.set(prpDir, id, i -> {
                    var d = i.deps() == null ? new ArrayList<Long>() : new ArrayList<>(i.deps());
                    if (!d.contains(thisId)) d.add(thisId);
                    i.deps(d);
                }, session);
                continue;
            }
            var other = littlejlib.proj.track.ProjectScope.resolve(target.uuid() != null ? target.uuid() : target.project()).dir().resolve("prp");
            littlejlib.proj.track.Workplan.set(other, target.id(), i -> {
                var x = i.xdeps() == null ? new ArrayList<littlejlib.proj.track.Link>() : new ArrayList<>(i.xdeps());
                if (x.stream().noneMatch(l -> l.key().equalsIgnoreCase(me.key()))) x.add(me);
                i.xdeps(x);
            }, session);
        }
    }

    /** Applies whichever fields were given; null means "leave as it is". */
    static void apply(Item i, Path prpDir, String type, String status, String title, String detail, String prp,
                      List<String> related, List<String> after, List<String> refs, String owner, String source, String tag, List<String> uses) {
        if (uses != null) {
            var u = new ArrayList<String>();
            for (var t : uses) for (var part : t.split("[,\\s]+")) if (!part.isBlank()) u.add(part.strip());
            i.uses(u.isEmpty() ? null : u);
        }
        if (tag != null) i.tag(tag.isBlank() ? null : tag.strip().toLowerCase());
        if (type != null) i.type(type.strip().toLowerCase());
        if (status != null) i.status(status.strip().toLowerCase());
        if (title != null) i.title(title.strip());
        if (detail != null) i.detail(blankToNull(detail));
        if (prp != null) i.prp(prp.isBlank() ? null : TrackCli.target(prpDir, prp));
        if (related != null) i.related(uuids(prpDir, related));
        if (after != null) {
            var local = new ArrayList<Long>();
            var cross = new ArrayList<littlejlib.proj.track.Link>();
            for (var t : after) for (var part : t.split("[,\\s]+")) {
                if (part.isBlank()) continue;
                var link = link(prpDir, part.strip());
                if (link == null) local.add(Long.parseLong(part.strip().replaceAll("^.*[#:]", "")));
                else cross.add(link);
            }
            i.deps(local.isEmpty() ? null : local);
            i.xdeps(cross.isEmpty() ? null : cross);
        }
        if (refs != null) i.refs(refs.isEmpty() ? null : refs);
        if (owner != null) i.owner(blankToNull(owner));
        if (source != null) i.source(blankToNull(source));
    }
}
