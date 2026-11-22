package littlejlib.proj.prp;

import littlejlib.proj.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Guide sources (extend / replace lists, old single strings, offline use of the local copy, git over a
 * local bare repository) on invented fixtures. user.home points into a temp folder, so the real
 * settings and cache are never touched.
 *
 * mvn -q -o test-compile exec:java -Dexec.mainClass="littlejlib.proj.prp.SmokeGuideSources" -Dexec.classpathScope=test
 */
public class SmokeGuideSources {

    static Path tmp, hooks;

    public static void main(String[] args) throws Exception {
        tmp = Files.createTempDirectory("guidesources");
        hooks = Files.createDirectories(tmp.resolve("nohooks"));
        System.setProperty("user.home", tmp.resolve("home").toString());
        GitCache.timeoutSeconds = 60;
        order();
        compat();
        offlineUrl();
        parsing();
        git();
        System.out.println("ALL PASSED");
        System.exit(0);
    }

    static Path file(String name, String text) throws Exception {
        var f = tmp.resolve("files").resolve(name);
        Files.createDirectories(f.getParent());
        return Files.writeString(f, text);
    }

    static Settings settings(String... entries) {
        var s = new Settings();
        s.setPrpGuide(List.of(entries));
        return s;
    }

    static Path project(String... entries) {
        var p = tmp.resolve("proj" + System.nanoTime());
        if (entries.length > 0) ProjFile.save(p, new ProjJson().prpGuide(List.of(entries)));
        return p;
    }

    static void order() throws Exception {
        var a = file("a.md", "TEXT-A\n"); var b = file("b.md", "TEXT-B\n"); var c = file("c.md", "TEXT-C\n");
        var base = PrpGuide.bundled().stripTrailing();
        var g = PrpGuide.load(null, settings(), false);
        check(g.text().equals(base + "\n") && g.origins().size() == 1, "no entries: the bundled guide alone");
        g = PrpGuide.load(null, settings("extend:" + a, "extend:" + b), false);
        check(g.text().equals(base + "\n\nTEXT-A\n\nTEXT-B\n"), "extend: after the base, in list order");
        check(g.origins().size() == 3 && g.origins().get(1).contains("extend " + a), "one origin line per source: " + g.origins());
        g = PrpGuide.load(null, settings("extend:" + a, "replace:" + b, "extend:" + c), false);
        check(g.text().equals("TEXT-B\n\nTEXT-C\n"), "replace drops the base and everything before it");
        g = PrpGuide.load(project("extend:" + c), settings("replace:" + b), false);
        check(g.text().equals("TEXT-B\n\nTEXT-C\n"), "the project list is applied after the global list");
        g = PrpGuide.load(project("replace:" + c), settings("extend:" + a), false);
        check(g.text().equals("TEXT-C\n"), "a project replace wins over the global list");
        g = PrpGuide.load(project("replace:bundled"), settings("replace:" + b), false);
        check(g.text().equals(base + "\n"), "replace:bundled resets to the base");
        g = PrpGuide.load(null, settings("extend:" + tmp.resolve("missing.md"), "extend:" + a), false);
        check(g.text().equals(base + "\n\nTEXT-A\n") && g.origins().get(g.origins().size() - 1).contains("SKIPPED"), "an unreadable entry is skipped with a note");
        pass("extend and replace order");
    }

    static void compat() throws Exception {
        var a = file("old.md", "TEXT-OLD\n");
        var p = tmp.resolve("oldproj");
        Files.createDirectories(p.resolve("prp"));
        Files.writeString(p.resolve("prp").resolve("proj.json"), "{\"name\":\"billing\",\"prpGuide\":\"" + a.toString().replace("\\", "\\\\") + "\"}");
        var read = ProjFile.read(p);
        check(read.prpGuide().equals(List.of("replace:" + a)), "old proj.json string is one replace entry: " + read.prpGuide());
        check(PrpGuide.load(p, settings(), false).text().equals("TEXT-OLD\n"), "old project string replaces the base, as before");
        check(GuideSource.parse(a.toString()).replace(), "a bare entry is replace");
        ProjFile.save(p, read);
        check(Files.readString(ProjFile.file(p)).contains("[") && ProjFile.read(p).prpGuide().equals(read.prpGuide()), "saved back as a list");
        pass("old single-string values");
    }

    static void offlineUrl() throws Exception {
        var url = "https://example.invalid/guides/team.md";
        var g = PrpGuide.load(null, settings("extend:" + url), false);
        check(g.origins().get(g.origins().size() - 1).contains("SKIPPED") && !g.text().contains("CACHED"), "unreachable url, nothing cached: skipped");
        var cached = GithubCache.fileFor(url);
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "CACHED-TEAM\n");
        Files.setLastModifiedTime(cached, FileTime.from(java.time.Instant.now().minusSeconds(3 * 86400)));
        g = PrpGuide.load(null, settings("extend:" + url), true);
        check(g.text().contains("CACHED-TEAM"), "unreachable url, stale or refreshed: the local copy is used");
        var line = g.origins().get(g.origins().size() - 1);
        check(line.contains("LOCAL COPY") && line.contains(cached.toString()) && line.contains("fetched"), "origin names the local copy: " + line);
        pass("offline use of a cached url");
    }

    static void parsing() {
        var s = GuideSource.parse("extend:git+https://github.com/acme/guides/blob/main/docs/g.md").gitFile();
        check(s != null && s.remote().equals("git@github.com:acme/guides.git") && s.ref().equals("main") && s.path().equals("docs/g.md"), "git+https blob form");
        check(s.cloneDir().endsWith(Path.of("githubcache", "acme", "guides")), "clone dir is githubcache/org/repo: " + s.cloneDir());
        s = GuideSource.parse("git@github.com:acme/guides.git#v2:g.md").gitFile();
        check(s.remote().equals("git@github.com:acme/guides.git") && s.ref().equals("v2") && s.dir().isEmpty(), "scp form, file at the root");
        check(GuideSource.parse("git@github.com:acme/guides.git#main:../x").gitFile() == null, "a path that climbs out is refused");
        check(!GuideSource.parse("https://example.invalid/x.md").isGit() && !GuideSource.parse("C:\\docs\\g.md").isGit(), "urls and paths are not git");
        check(!GuideSource.parse("extend:https://user:secret@example.invalid/x.md").shown().contains("secret"), "credentials are not shown");
        check(!GitCache.tail("fatal: unable to access 'https://user:secret@example.invalid/x'").contains("secret"), "git errors hide credentials");
        pass("git source syntax");
    }

    static void git() throws Exception {
        var bare = tmp.resolve("remote").resolve("acme").resolve("guides.git");
        var work = tmp.resolve("work");
        Files.createDirectories(bare);
        git(bare, "init", "--bare", "-b", "main");
        git(bare, "config", "uploadpack.allowFilter", "true");
        git(bare, "config", "uploadpack.allowAnySHA1InWant", "true");
        Files.createDirectories(work);
        git(work, "init", "-b", "main");
        var pushed = push(work, bare, "GIT-V1\n", "top-v1\n");
        var spec = "file:///" + bare.toString().replace('\\', '/').replaceFirst("^/+", "");
        var entry = "extend:" + spec + "#main:docs/guide.md";
        var g = PrpGuide.load(null, settings(entry), false);
        var line = g.origins().get(g.origins().size() - 1);
        check(g.text().contains("GIT-V1") && line.contains("fetched with git") && line.contains("LOCAL COPY"), "first load clones and reads: " + line);
        var local = GuideSource.parse(entry).gitFile().file();
        check(Files.isRegularFile(local) && line.contains(local.toString()), "kept under githubcache: " + local);
        check(local.startsWith(GithubCache.root()), "inside the cache root");
        check(!Files.exists(local.getParent().getParent().resolve("other")), "sparse: only the wanted directory is checked out");
        push(work, bare, "GIT-V2\n", "top-v2\n");
        check(PrpGuide.load(null, settings(entry), false).text().contains("GIT-V1"), "within a day the local copy is used without fetching");
        check(PrpGuide.load(null, settings(entry), true).text().contains("GIT-V2"), "--refresh fetches the update");
        var gone = tmp.resolve("remote-away");
        Files.move(bare.getParent(), gone);
        check(PrpGuide.load(null, settings(entry), false).text().contains("GIT-V2"), "offline, not stale: no git needed");
        var stamp = GuideSource.parse(entry).gitFile().cloneDir().resolve(".git").resolve(GuideSource.parse(entry).gitFile().stampName());
        Files.setLastModifiedTime(stamp, FileTime.from(java.time.Instant.now().minusSeconds(3 * 86400)));
        g = PrpGuide.load(null, settings(entry), false);
        line = g.origins().get(g.origins().size() - 1);
        check(g.text().contains("GIT-V2") && line.contains("WARNING"), "offline and stale: git fails, the local copy is used with a warning: " + line);
        Files.move(gone, bare.getParent());
        g = PrpGuide.load(null, settings(entry), false);
        check(g.text().contains("GIT-V2") && !g.origins().get(g.origins().size() - 1).contains("WARNING"), "back online: refreshed after a day");
        var root = "extend:" + spec + "#main:top.md";
        check(PrpGuide.load(null, settings(root), true).text().contains("top-v2"), "a file at the repository root, second path in the same clone");
        g = PrpGuide.load(null, settings("extend:" + spec + "#main:docs/none.md"), true);
        check(g.origins().get(g.origins().size() - 1).contains("SKIPPED") && g.text().indexOf("GIT-") < 0, "a path that is not in the repository is skipped");
        g = PrpGuide.load(null, settings("extend:file:///" + tmp.resolve("no-such").toString().replace('\\', '/') + ".git#main:g.md"), false);
        check(g.origins().get(g.origins().size() - 1).contains("SKIPPED"), "an unreachable repository with no copy is skipped, base kept");
        pass("git source over a local bare repository (" + pushed + ")");
    }

    static String push(Path work, Path bare, String doc, String top) throws Exception {
        Files.createDirectories(work.resolve("docs"));
        Files.createDirectories(work.resolve("other"));
        Files.writeString(work.resolve("docs").resolve("guide.md"), doc);
        Files.writeString(work.resolve("other").resolve("x.md"), "x\n");
        Files.writeString(work.resolve("top.md"), top);
        git(work, "add", "-A");
        git(work, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "-c", "commit.gpgsign=false", "commit", "-q", "-m", "c");
        git(work, "push", "-q", bare.toUri().toString().replaceAll("/$", ""), "main");
        return doc.strip();
    }

    static void git(Path dir, String... args) throws Exception {
        var cmd = new ArrayList<>(List.of("git", "-c", "core.hooksPath=" + hooks));
        cmd.addAll(List.of(args));
        var log = Files.createTempFile(tmp, "git", ".log");
        var p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) throw new AssertionError("git " + String.join(" ", args) + ": " + Files.readString(log));
    }

    static void pass(String what) {
        System.out.println("  pass  " + what);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
