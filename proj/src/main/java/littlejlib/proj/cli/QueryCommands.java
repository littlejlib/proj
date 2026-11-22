package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import littlejlib.proj.CodeRepoBaseType;
import littlejlib.proj.Settings;
import littlejlib.proj.SettingsManager;
import littlejlib.proj.central.CentralChecker;
import littlejlib.proj.model.*;
import littlejlib.proj.prp.PrpCreator;
import littlejlib.proj.scanner.ProjectStore;
import littlejlib.proj.ui.ConcernFilter;
import littlejlib.proj.ui.ProjectFilter;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only half of proj, as commands rather than a window.
 * <p>
 * Everything the picker paints is a fact already sitting in the index - which projects exist, what
 * they are built with, where they are pushed, what they depend on, whether any of it is published.
 * A window is a fine way to browse those facts and a useless way to answer a question inside a
 * script or from an agent, which is what these are for. Tab-separated by default so the output
 * pipes; {@code --json} when the consumer wants structure.
 * <p>
 * Every command here is READ-ONLY and none of them scans. They report what the last scan found, and
 * say when that was, rather than quietly triggering a filesystem walk that the caller did not ask
 * for and would have to wait through.
 */
public final class QueryCommands {

    private QueryCommands() { }

    public static int run(List<String> args, PrintWriter out, PrintWriter err) {
        var root = new CommandLine(new Root());
        root.addSubcommand("list", new ListCmd(out));
        root.addSubcommand("find", new FindCmd(out));
        root.addSubcommand("info", new InfoCmd(out, err));
        root.addSubcommand("deps", new DepsCmd(out, err));
        root.addSubcommand("central", new CentralCmd(out));
        root.addSubcommand("scan", new ScanCmd(out));
        root.addSubcommand("files", new FilesCmd(out));
        root.addSubcommand("where", new WhereCmd(out, err));
        root.addSubcommand("impact", new ImpactCmd(out, err));
        root.addSubcommand("blocked", new BlockedCmd(out));
        root.addSubcommand("ws", new WsCli());
        root.addSubcommand("prp-session", new PrpSessionCmd(out, err));
        root.addSubcommand("cct", new littlejlib.proj.cct.cli.CctCli());
        root.setOut(out);
        root.setErr(err);
        littlejlib.proj.cct.cli.Out.redirect(out, err);
        root.setUnmatchedArgumentsAllowed(false);
        // picocli's default handler prints a stack trace, which is never the useful answer here -
        // an index held by another process, say, needs one line telling you to close that window.
        root.setExecutionExceptionHandler((e, cmd, parse) -> {
            err.println(littlejlib.proj.cct.cli.CctCli.message(e));
            err.flush();
            return 3;
        });
        try {
            var code = root.execute(args.toArray(new String[0]));
            if (System.getenv("PROJ_CLI_TRACE") != null) System.err.println("[proj] picocli returned " + code);
            return code;
        } catch (Exception e) {
            err.println("query failed: " + e.getMessage());
            return 3;
        } finally {
            // picocli writes usage and any exception from a subcommand to the writers set above and
            // does NOT flush them. Without this a command that fails prints absolutely nothing -
            // which is how a silent failure looks exactly like a command with no output.
            littlejlib.proj.cct.cli.Out.clear();
            out.flush();
            err.flush();
        }
    }

    @Command(name = "query", description = "Read the project index")
    static class Root implements Runnable {
        @Override
        public void run() {
            // picocli prints usage when no subcommand is given
            CommandLine.usage(this, System.out);
        }
    }

    // ---------- shared loading ----------

    /**
     * Everything the picker holds in memory, loaded once per command.
     * <p>
     * The Central answers come only from the cache. A command must not go to the network on its own
     * initiative - a script asking "which projects are not published" should get the answer that is
     * known, plus a count of what has not been looked up, and decide for itself whether to spend
     * eight hundred HTTP requests on the rest. {@code proj central --sweep} is that decision, made
     * explicitly.
     */
    record Loaded(Settings settings, List<ProjectRecord> records, DependencyIndex index,
                  CentralChecker central, Map<String, ProjectConcerns> concerns) {

        static Loaded read() {
            var settings = new SettingsManager().loadSettings();
            var store = new ProjectStore();
            var records = new ArrayList<ProjectRecord>();
            for (var p : settings.getBrowsePaths()) records.addAll(store.load(Path.of(p)));

            var index = new DependencyIndex(records, store.allDependencies());
            var central = new CentralChecker();
            central.warmFromCache();

            var concerns = new LinkedHashMap<String, ProjectConcerns>();
            for (var r : records) {
                // Plain folders publish nothing and depend on nothing, so there is no concern to
                // compute; a missing entry already reads as "no finding" everywhere it is consulted.
                if (!r.isCodeDirNode() && !r.isFolder()) {
                    concerns.put(r.path(), ProjectConcerns.of(r, index, central::statusOf));
                }
            }
            return new Loaded(settings, records, index, central, concerns);
        }

        /**
         * Just the project rows: no dependency graph, no Central cache, no concerns.
         * <p>
         * A file lookup needs projects only in order to name the owner and honour the base filter.
         * Going through read() for that walked every dependency of all 2,269 projects to compute
         * concerns nothing was going to look at, which is most of the cost of answering "where is
         * this class".
         */
        static Loaded projectsOnly() {
            var settings = new SettingsManager().loadSettings();
            var store = new ProjectStore();
            var records = new ArrayList<ProjectRecord>();
            for (var p : settings.getBrowsePaths()) records.addAll(store.load(Path.of(p)));
            return new Loaded(settings, records, new DependencyIndex(List.of(), List.of()),
                    new CentralChecker(), Map.of());
        }

        ProjectConcerns concernsOf(ProjectRecord r) {
            return r == null ? null : concerns.get(r.path());
        }

        /** The indexed project a path is inside, nearest first - so "info" works from any subdirectory. */
        ProjectRecord resolve(String pathOrNull) {
            var start = Path.of(pathOrNull == null || pathOrNull.isBlank()
                    ? System.getProperty("user.dir") : pathOrNull).toAbsolutePath().normalize();

            var byPath = new java.util.HashMap<String, ProjectRecord>();
            for (var r : records) byPath.put(r.path(), r);

            for (var p = start; p != null; p = p.getParent()) {
                var hit = byPath.get(p.toString());
                if (hit != null) return hit;
            }
            return null;
        }
    }

    // ---------- argument converters ----------

    /**
     * Accepts the names a person would actually type. The enum constant is NOT_ON_CENTRAL and
     * nobody types that at a shell prompt; hyphens and case are absorbed so the documented spelling
     * and the working spelling are the same string.
     */
    public static class ConcernConverter implements CommandLine.ITypeConverter<ConcernFilter> {
        @Override
        public ConcernFilter convert(String value) {
            var key = value.trim().replace('-', '_').toUpperCase();
            for (var f : ConcernFilter.values()) if (f.name().equals(key)) return f;
            throw new CommandLine.TypeConversionException("unknown concern '" + value + "'. One of: "
                    + java.util.Arrays.stream(ConcernFilter.values())
                            .map(f -> f.name().toLowerCase().replace('_', '-')).toList());
        }
    }

    public static class BaseConverter implements CommandLine.ITypeConverter<CodeRepoBaseType> {
        @Override
        public CodeRepoBaseType convert(String value) {
            var parsed = CodeRepoBaseType.parse(value, null);
            if (parsed == null) {
                throw new CommandLine.TypeConversionException(
                        "unknown coderepobase type '" + value + "'. One of: primary, mirror, auxiliary");
            }
            return parsed;
        }
    }

    // ---------- output helpers ----------

    static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static void emit(PrintWriter out, boolean json, List<Map<String, Object>> rows, String... columns) {
        if (System.getenv("PROJ_CLI_TRACE") != null) System.err.println("[proj] emit rows=" + rows.size());
        if (json) {
            out.println(GSON.toJson(rows));
        } else {
            out.println(String.join("\t", columns));
            for (var row : rows) {
                var cells = new ArrayList<String>(columns.length);
                for (var c : columns) cells.add(nz(row.get(c)));
                out.println(String.join("\t", cells));
            }
        }
        out.flush();
    }

    static Map<String, Object> describe(ProjectRecord r, ProjectConcerns c) {
        var m = new LinkedHashMap<String, Object>();
        m.put("project", r.relativePath());
        m.put("coordinates", r.coordinates());
        m.put("version", nz(r.version()));
        m.put("build", build(r));
        m.put("git", r.gitState().label);
        m.put("central", c == null ? "" : c.central().name().toLowerCase());
        m.put("deps", c == null ? 0 : c.dependencyCount());
        m.put("local", c == null ? 0 : c.localDependencies().size());
        m.put("absent", c == null ? 0 : c.absentDependencies().size());
        m.put("path", r.path());
        return m;
    }

    static final String[] COLUMNS =
            {"project", "coordinates", "build", "git", "central", "deps", "local", "absent", "path"};

    static String build(ProjectRecord r) {
        if (r.isFolder()) return "folder";
        var sb = new StringBuilder();
        if (r.maven()) sb.append("maven,");
        if (r.gradle()) sb.append("gradle,");
        if (r.jbang()) sb.append("jbang,");
        if (r.git()) sb.append("git,");
        return sb.isEmpty() ? "" : sb.substring(0, sb.length() - 1);
    }

    static String nz(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    // ---------- list ----------

    @Command(name = "list", aliases = "ls", description = "List indexed projects")
    static class ListCmd implements Runnable {
        final PrintWriter out;

        ListCmd(PrintWriter out) {
            this.out = out;
        }

        @Option(names = "--maven", description = "Only maven projects")
        boolean maven;

        // Off by default, which is the opposite of the picker on purpose: the window is for
        // browsing and a folder is a place to go, while this is read by scripts and agents asking
        // about projects. Turning it on here would silently change what every existing caller sees.
        @Option(names = "--folders", description = "Also list plain directories - no build file, nothing under git")
        boolean folders;

        @Option(names = "--concern", converter = ConcernConverter.class,
                description = "all, maven, not-maven, folder, not-on-central, deps-not-on-central, "
                        + "has-local-deps, not-on-github, no-remote, no-git")
        ConcernFilter concern = ConcernFilter.ALL;

        @Option(names = "--base", converter = BaseConverter.class,
                description = "Only this coderepobase type: primary, mirror, auxiliary")
        CodeRepoBaseType base;

        @Option(names = "--limit", description = "Stop after this many rows")
        int limit = 0;

        @Option(names = "--paths", description = "Print absolute paths only, one per line - for piping")
        boolean pathsOnly;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            if (System.getenv("PROJ_CLI_TRACE") != null) System.err.println("[proj] ListCmd.run");
            var loaded = Loaded.read();
            if (System.getenv("PROJ_CLI_TRACE") != null) {
                System.err.println("[proj] loaded records=" + loaded.records().size());
            }
            var typeOf = ProjectFilter.resolver(loaded.settings());

            var rows = new ArrayList<Map<String, Object>>();
            for (var r : loaded.records()) {
                if (r.isCodeDirNode()) continue;
                if (r.isFolder() && !(folders || concern == ConcernFilter.FOLDER)) continue;
                if (maven && !r.maven()) continue;
                // With no --base the default is what the picker defaults to: the primary base only.
                // A mirror of everything on GitHub would otherwise drown every listing.
                var type = typeOf.apply(r);
                if (base == null ? type != CodeRepoBaseType.PRIMARY : type != base) continue;
                if (!concern.test(r, loaded.concernsOf(r))) continue;

                rows.add(describe(r, loaded.concernsOf(r)));
                if (limit > 0 && rows.size() >= limit) break;
            }

            if (pathsOnly) {
                rows.forEach(m -> out.println(m.get("path")));
                out.flush();
                return;
            }
            emit(out, json, rows, COLUMNS);
        }
    }

    // ---------- find ----------

    @Command(name = "find", description = "Search indexed projects by path or maven coordinates")
    static class FindCmd implements Runnable {
        final PrintWriter out;

        FindCmd(PrintWriter out) {
            this.out = out;
        }

        @Parameters(arity = "1..*", description = "Search terms, matched the same way the picker matches them")
        List<String> terms;

        @Option(names = "--all-bases", description = "Include the GitHub mirror and auxiliary bases")
        boolean allBases;

        @Option(names = "--folders", description = "Also match plain directories - no build file, nothing under git")
        boolean folders;

        @Option(names = "--paths", description = "Print absolute paths only, one per line")
        boolean pathsOnly;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();
            var typeOf = ProjectFilter.resolver(loaded.settings());
            var match = ProjectFilter.textOnly(String.join(" ", terms), false, folders);

            var rows = new ArrayList<Map<String, Object>>();
            for (var r : loaded.records()) {
                if (r.isCodeDirNode() || !match.test(r)) continue;
                if (!allBases && typeOf.apply(r) != CodeRepoBaseType.PRIMARY) continue;
                rows.add(describe(r, loaded.concernsOf(r)));
            }

            if (pathsOnly) {
                rows.forEach(m -> out.println(m.get("path")));
                out.flush();
                return;
            }
            emit(out, json, rows, COLUMNS);
        }
    }

    // ---------- info ----------

    @Command(name = "info", description = "Everything known about one project")
    static class InfoCmd implements Runnable {
        final PrintWriter out, err;

        InfoCmd(PrintWriter out, PrintWriter err) {
            this.out = out;
            this.err = err;
        }

        @Parameters(arity = "0..1", description = "Project directory; defaults to the current directory")
        String path;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();
            var rec = loaded.resolve(path);
            if (rec == null) {
                err.println("not in the index: " + (path == null ? System.getProperty("user.dir") : path));
                err.println("run 'proj o' once, or 'proj daemon restart', to rebuild it");
                err.flush();
                return;
            }

            var c = loaded.concernsOf(rec);
            var m = new LinkedHashMap<String, Object>(describe(rec, c));
            m.put("name", rec.name());
            m.put("codeDir", rec.codeDir());
            m.put("packaging", nz(rec.packaging()));
            m.put("description", nz(rec.description()));
            m.put("gitRemote", nz(rec.gitRemote()));
            m.put("gitState", rec.gitState().name().toLowerCase());
            m.put("openCount", rec.openCount());
            m.put("lastScanned", nz(rec.lastScanned()));

            var prpDir = PrpCreator.prpDirOf(rec.dir());
            var prps = new ArrayList<String>();
            try {
                if (Files.isDirectory(prpDir)) PrpCreator.list(prpDir).forEach(p -> prps.add(p.getDisplayName()));
            } catch (Exception e) {
                err.println("could not read " + prpDir + ": " + e.getMessage());
            }
            m.put("prps", prps);
            m.put("blockedBy", c == null ? List.of()
                    : c.blockedBy().stream().map(ProjectRecord::relativePath).distinct().toList());
            m.put("dependents", loaded.index().dependentsOf(rec).stream()
                    .map(ProjectRecord::relativePath).toList());

            if (json) {
                out.println(GSON.toJson(m));
            } else {
                m.forEach((k, v) -> out.println(pad(k) + render(v)));
            }
            out.flush();
        }

        static String pad(String key) {
            return (key + "                ").substring(0, 16);
        }

        static String render(Object v) {
            if (v instanceof List<?> list) {
                return list.isEmpty() ? "-" : String.join(", ", list.stream().map(String::valueOf).toList());
            }
            return nz(v);
        }
    }

    // ---------- deps ----------

    @Command(name = "deps", description = "Dependencies of a project, or what depends on it")
    static class DepsCmd implements Runnable {
        final PrintWriter out, err;

        DepsCmd(PrintWriter out, PrintWriter err) {
            this.out = out;
            this.err = err;
        }

        @Parameters(arity = "0..1", description = "Project directory; defaults to the current directory")
        String path;

        @Option(names = "--local", description = "Only dependencies provided by another indexed project")
        boolean local;

        @Option(names = "--absent", description = "Only dependencies that were looked up and NOT found on Central")
        boolean absent;

        @Option(names = "--reverse", description = "Instead list the indexed projects that depend on this one")
        boolean reverse;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();
            var rec = loaded.resolve(path);
            if (rec == null) {
                err.println("not in the index: " + (path == null ? System.getProperty("user.dir") : path));
                err.flush();
                return;
            }

            if (reverse) {
                var rows = loaded.index().dependentsOf(rec).stream()
                        .map(r -> describe(r, loaded.concernsOf(r))).toList();
                emit(out, json, rows, COLUMNS);
                return;
            }

            var rows = new ArrayList<Map<String, Object>>();
            for (var d : loaded.index().of(rec.path())) {
                var provider = loaded.index().localProviderOf(d);
                var status = loaded.central().statusOf(d.coordinates());
                if (local && provider == null) continue;
                if (absent && status != CentralStatus.ABSENT) continue;

                var m = new LinkedHashMap<String, Object>();
                m.put("groupId", d.groupId());
                m.put("artifactId", d.artifactId());
                m.put("version", d.versionResolved() ? d.version() : "");
                m.put("scope", nz(d.scope()));
                m.put("managed", d.managed());
                m.put("central", d.versionResolved() ? status.name().toLowerCase() : "unversioned");
                m.put("localProvider", provider == null ? "" : provider.relativePath());
                rows.add(m);
            }
            emit(out, json, rows,
                    "groupId", "artifactId", "version", "scope", "managed", "central", "localProvider");
        }
    }

    // ---------- files ----------

    /**
     * Finding a source file by name, across every indexed project.
     * <p>
     * Not a replacement for {@code rg --files -g}, which is measurably faster at exactly this - see
     * {@link littlejlib.proj.model.FileRecord}. What this adds is
     * attribution: the owning project, the package, the fully qualified name, and the ability to
     * restrict a lookup to one project or to your own code rather than a third-party mirror.
     */
    @Command(name = "files", aliases = "f",
            description = "Find source files by name across every indexed project")
    static class FilesCmd implements Runnable {
        final PrintWriter out;

        FilesCmd(PrintWriter out) {
            this.out = out;
        }

        @Parameters(arity = "0..*", description = "Name fragments. 'term runner' matches TerminalRunner")
        List<String> terms = new ArrayList<>();

        @Option(names = "--exact", description = "The file's base name must equal this exactly, e.g. --exact TerminalRunner")
        String exact;

        @Option(names = "--ext", description = "Only this extension, e.g. --ext java")
        String ext;

        @Option(names = "--project", description = "Only inside this project directory")
        String project;

        @Option(names = "--all-bases", description = "Include the GitHub mirror and auxiliary bases")
        boolean allBases;

        @Option(names = "--paths", description = "Absolute paths only, one per line - pipe this into ripgrep")
        boolean pathsOnly;

        @Option(names = "--limit", description = "Stop after this many (default 200; 0 for no limit)")
        int limit = 200;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var store = new ProjectStore();
            if (store.fileCount() == 0) {
                out.println("no files indexed yet - run 'proj scan' (file indexing is part of a scan)");
                out.flush();
                return;
            }

            var loaded = Loaded.projectsOnly();
            var typeOf = ProjectFilter.resolver(loaded.settings());
            var byPath = new java.util.HashMap<String, ProjectRecord>();
            for (var r : loaded.records()) byPath.put(r.path(), r);

            // An exact stem is an index hit; anything else has to be filtered, so the exact case is
            // kept distinct rather than folded into a general matcher that would scan every row.
            var candidates = exact != null && !exact.isBlank()
                    ? store.filesByStem(exact.trim())
                    : store.allFiles();

            java.util.function.Predicate<littlejlib.proj.model.FileRecord> matcher =
                    terms.isEmpty() ? f -> true : nameMatcher(String.join(" ", terms));

            var rows = new ArrayList<Map<String, Object>>();
            for (var f : candidates) {
                if (ext != null && !ext.isBlank() && !ext.trim().equalsIgnoreCase(f.ext())) continue;
                if (!matcher.test(f)) continue;

                var owner = byPath.get(f.projectPath());
                if (owner == null) continue;
                if (!allBases && typeOf.apply(owner) != CodeRepoBaseType.PRIMARY) continue;
                if (project != null && !project.isBlank()
                        && !samePath(owner.path(), project) && !owner.relativePath().equalsIgnoreCase(project)) {
                    continue;
                }

                var m = new LinkedHashMap<String, Object>();
                m.put("file", f.name());
                m.put("fqn", f.fqn());
                m.put("project", owner.relativePath());
                m.put("relPath", f.relPath());
                m.put("path", f.path());
                rows.add(m);
                if (limit > 0 && rows.size() >= limit) break;
            }

            if (pathsOnly) {
                rows.forEach(m -> out.println(m.get("path")));
                out.flush();
                return;
            }
            emit(out, json, rows, "file", "fqn", "project", "relPath", "path");
        }

        /** Same fuzzy shape as the picker's filter, matched against the name and the package. */
        static java.util.function.Predicate<littlejlib.proj.model.FileRecord>
                nameMatcher(String text) {
            try {
                var pattern = java.util.regex.Pattern.compile(String.join(".*", text.trim().split("\\s+")),
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                return f -> pattern.matcher(f.name()).find() || pattern.matcher(nz(f.pkg())).find();
            } catch (Exception e) {
                var lower = text.toLowerCase();
                return f -> f.name().toLowerCase().contains(lower);
            }
        }

        static boolean samePath(String a, String b) {
            try {
                return Path.of(a).toAbsolutePath().normalize()
                        .equals(Path.of(b).toAbsolutePath().normalize());
            } catch (Exception e) {
                return false;
            }
        }
    }

    // ---------- where ----------

    /**
     * Which project publishes these coordinates. grep can find the string in a pom; it cannot tell
     * you that the version resolved through a parent, nor that this is the project that PROVIDES
     * the artifact rather than one of the dozen that consume it.
     */
    @Command(name = "where", description = "Which indexed project provides these maven coordinates")
    static class WhereCmd implements Runnable {
        final PrintWriter out, err;

        WhereCmd(PrintWriter out, PrintWriter err) {
            this.out = out;
            this.err = err;
        }

        @Parameters(arity = "1", description = "groupId:artifactId, or just an artifactId")
        String coordinates;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();
            var wanted = coordinates.trim();

            var rows = new ArrayList<Map<String, Object>>();
            for (var r : loaded.records()) {
                if (r.isCodeDirNode() || !r.maven()) continue;
                var ga = ProjectDependency.ga(r.groupId(), r.artifactId());
                var matches = wanted.contains(":")
                        ? ga.equalsIgnoreCase(wanted)
                        : wanted.equalsIgnoreCase(nz(r.artifactId()));
                if (!matches) continue;

                var m = new LinkedHashMap<String, Object>(describe(r, loaded.concernsOf(r)));
                m.put("consumers", loaded.index().dependentsOf(r).size());
                rows.add(m);
            }

            if (rows.isEmpty()) {
                err.println("no indexed project provides " + wanted);
                err.flush();
                return;
            }
            emit(out, json, rows, "project", "coordinates", "central", "consumers", "path");
        }
    }

    // ---------- impact ----------

    /**
     * What breaks if this project changes: its local dependents, and theirs, transitively.
     * <p>
     * The transitive part is what makes this worth a command. One hop is a grep for the artifactId
     * across poms; the second hop needs the answer to the first, and by the third nobody is doing
     * this by hand.
     */
    @Command(name = "impact", description = "Projects that break if this one changes, transitively")
    static class ImpactCmd implements Runnable {
        final PrintWriter out, err;

        ImpactCmd(PrintWriter out, PrintWriter err) {
            this.out = out;
            this.err = err;
        }

        @Parameters(arity = "0..1", description = "Project directory; defaults to the current directory")
        String path;

        @Option(names = "--depth", description = "How many hops to follow (default: all)")
        int depth = Integer.MAX_VALUE;

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();
            var start = loaded.resolve(path);
            if (start == null) {
                err.println("not in the index: " + (path == null ? System.getProperty("user.dir") : path));
                err.flush();
                return;
            }

            // Breadth first, so the hop count reported is the shortest one - "how far away is this"
            // is the useful number, not "how did the walk happen to reach it".
            var seen = new java.util.LinkedHashMap<String, Integer>();
            var frontier = new ArrayList<ProjectRecord>(List.of(start));
            seen.put(start.path(), 0);

            for (var hop = 1; hop <= depth && !frontier.isEmpty(); hop++) {
                var next = new ArrayList<ProjectRecord>();
                for (var current : frontier) {
                    for (var dependent : loaded.index().dependentsOf(current)) {
                        if (seen.containsKey(dependent.path())) continue;
                        seen.put(dependent.path(), hop);
                        next.add(dependent);
                    }
                }
                frontier = next;
            }

            var rows = new ArrayList<Map<String, Object>>();
            seen.forEach((p, hop) -> {
                if (hop == 0) return;                 // the project itself is not its own impact
                var r = loaded.index().byPath(p);
                if (r == null) return;
                var m = new LinkedHashMap<String, Object>();
                m.put("hops", hop);
                m.putAll(describe(r, loaded.concernsOf(r)));
                rows.add(m);
            });
            rows.sort(java.util.Comparator.comparingInt(m -> (Integer) m.get("hops")));

            emit(out, json, rows, "hops", "project", "coordinates", "central", "path");
        }
    }

    // ---------- blocked ----------

    /**
     * Projects that cannot be published because something local that they depend on is not
     * published, listed so that the things nothing is waiting on come first.
     * <p>
     * This is the question the whole dependency index was built to answer: not "what is missing"
     * but "what do I release first". It cannot be greppped for at any price - it needs the local
     * provider mapping and the Central answers together.
     */
    @Command(name = "blocked", description = "Projects blocked from publishing, in release order")
    static class BlockedCmd implements Runnable {
        final PrintWriter out;

        BlockedCmd(PrintWriter out) {
            this.out = out;
        }

        @Option(names = "--json", description = "JSON instead of tab separated")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();
            var typeOf = ProjectFilter.resolver(loaded.settings());

            var rows = new ArrayList<Map<String, Object>>();
            for (var r : loaded.records()) {
                if (r.isCodeDirNode() || !r.maven()) continue;
                if (typeOf.apply(r) != CodeRepoBaseType.PRIMARY) continue;

                var c = loaded.concernsOf(r);
                if (c == null || c.blockedBy().isEmpty()) continue;

                var blockers = c.blockedBy().stream()
                        .map(ProjectRecord::relativePath).distinct().sorted().toList();
                var m = new LinkedHashMap<String, Object>();
                m.put("project", r.relativePath());
                m.put("coordinates", r.coordinates());
                m.put("blockers", blockers.size());
                m.put("blockedBy", String.join(", ", blockers));
                m.put("path", r.path());
                rows.add(m);
            }

            // Fewest blockers first: those are the ones that become releasable soonest, and
            // releasing them is what unblocks everything behind them.
            rows.sort(java.util.Comparator.comparingInt(m -> (Integer) m.get("blockers")));
            emit(out, json, rows, "blockers", "project", "coordinates", "blockedBy", "path");
        }
    }

    // ---------- scan ----------

    /**
     * The one command here that writes. It exists because everything else deliberately does not:
     * without it the only way to refresh the index would be to open the picker, which is exactly
     * the thing a script cannot do.
     */
    @Command(name = "scan", description = "Re-walk every coderepobase and rebuild the index")
    static class ScanCmd implements Runnable {
        final PrintWriter out;

        ScanCmd(PrintWriter out) {
            this.out = out;
        }

        @Option(names = "--central", description = "Also look up every coordinate on Maven Central afterwards")
        boolean sweepCentral;

        @Option(names = "--no-files", description = "Skip the source file index (faster, but 'proj files' goes stale)")
        boolean noFiles;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var settings = new SettingsManager().loadSettings();
            var store = new ProjectStore();
            var t0 = System.currentTimeMillis();

            var projects = 0;
            var folders = 0;
            var dependencies = 0;
            var files = 0;
            var perBase = new LinkedHashMap<String, Object>();

            for (var p : settings.getBrowsePaths()) {
                var dir = Path.of(p);
                if (!Files.isDirectory(dir)) continue;
                var scanner = new littlejlib.proj.scanner.ProjectTreeScanner(
                        settings.getProjectScanningDepth()).baseType(settings.typeOf(dir).name());
                var found = scanner.scan(dir);
                store.replaceAll(dir, found, scanner.dependencies(), System.currentTimeMillis());
                projects += scanner.projectsFound();
                folders += scanner.plainFoldersFound();
                dependencies += scanner.dependencies().size();
                perBase.put(p, found.size());

                // Only projects, deliberately. A file index exists to say which PROJECT owns a
                // source file, and walking every plain directory to the bottom would both bloat it
                // and hand back an owner that answers a different question.
                if (!noFiles) {
                    // A scan is the one moment worth paying for a full repair: an index left dirty
                    // by an earlier crash would otherwise fail the same way on every run after it.
                    store.deleteOrphanFiles();
                    files += indexFiles(store, found.stream().filter(ProjectRecord::isProject).toList());
                }
            }

            var ms = System.currentTimeMillis() - t0;

            if (sweepCentral) {
                var records = new ArrayList<ProjectRecord>();
                for (var p : settings.getBrowsePaths()) records.addAll(store.load(Path.of(p)));
                var index = new DependencyIndex(records, store.allDependencies());
                var checker = new CentralChecker();
                checker.warmFromCache();

                var latch = new java.util.concurrent.CountDownLatch(1);
                checker.sweep(index.allCoordinates(), (done, total) -> { }, latch::countDown);
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                checker.shutdown();
            }

            if (json) {
                var m = new LinkedHashMap<String, Object>();
                m.put("projects", projects);
                m.put("folders", folders);
                m.put("dependencies", dependencies);
                m.put("files", files);
                m.put("millis", ms);
                m.put("perBase", perBase);
                out.println(GSON.toJson(m));
            } else {
                perBase.forEach((base, n) -> out.println(n + "\t" + base));
                out.println(projects + " projects, " + folders + " folders, "
                        + dependencies + " dependencies, " + files + " files in " + ms + " ms");
            }
            out.flush();
        }

        /**
         * Indexes each project's own source files.
         * <p>
         * The set of every project path is passed as the stop list so a multi-module aggregator does
         * not also claim the files of its modules - otherwise a file under a module appears twice
         * and "which project owns this" stops having one answer.
         */
        static int indexFiles(ProjectStore store, List<ProjectRecord> projects) {
            var allPaths = projects.stream().map(ProjectRecord::path)
                    .collect(java.util.stream.Collectors.toSet());
            var indexer = new littlejlib.proj.scanner.FileIndexer();

            var found = new ArrayList<littlejlib.proj.model.FileRecord>();
            for (var p : projects) found.addAll(indexer.index(p, allPaths));

            store.replaceFiles(projects.stream().map(ProjectRecord::path).toList(), found);
            return found.size();
        }
    }

    // ---------- central ----------

    @Command(name = "central", description = "Maven Central publication status across the index")
    static class CentralCmd implements Runnable {
        final PrintWriter out;

        CentralCmd(PrintWriter out) {
            this.out = out;
        }

        @Option(names = "--sweep", description = "Look up every coordinate not already answered. "
                + "Hundreds of HTTP requests the first time; the answers are cached permanently")
        boolean sweep;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var loaded = Loaded.read();

            if (sweep) {
                var latch = new java.util.concurrent.CountDownLatch(1);
                loaded.central().sweep(loaded.index().allCoordinates(),
                        (done, total) -> {
                            if (done % 50 == 0) {
                                out.println("   " + done + " of " + total);
                                out.flush();
                            }
                        },
                        latch::countDown);
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            var published = 0;
            var missing = 0;
            var unchecked = 0;
            var notMaven = 0;
            var absent = new ArrayList<String>();

            for (var r : loaded.records()) {
                if (r.isCodeDirNode() || r.isFolder()) continue;
                if (!r.maven() || r.coordinates().isBlank()) {
                    notMaven++;
                    continue;
                }
                switch (loaded.central().statusOf(r.coordinates())) {
                    case PUBLISHED -> published++;
                    case ABSENT -> {
                        missing++;
                        absent.add(r.relativePath() + "\t" + r.coordinates());
                    }
                    default -> unchecked++;
                }
            }

            if (json) {
                var m = new LinkedHashMap<String, Object>();
                m.put("published", published);
                m.put("absent", missing);
                m.put("unchecked", unchecked);
                m.put("notMaven", notMaven);
                m.put("absentProjects", absent);
                out.println(GSON.toJson(m));
            } else {
                out.println("published   " + published);
                out.println("absent      " + missing);
                out.println("unchecked   " + unchecked
                        + (unchecked > 0 ? "   (run with --sweep to resolve these)" : ""));
                out.println("not maven   " + notMaven);
                if (!absent.isEmpty()) {
                    out.println();
                    out.println("Not on Maven Central:");
                    absent.forEach(a -> out.println("   " + a));
                }
            }
            out.flush();
        }
    }
}
