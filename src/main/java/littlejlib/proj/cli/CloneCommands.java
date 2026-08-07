package littlejlib.proj.cli;

import com.google.gson.GsonBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import littlejlib.proj.CodeRepoBaseType;
import littlejlib.proj.Settings;
import littlejlib.proj.SettingsManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

/**
 * Cloning a repository into the place it belongs, without prompting.
 * <p>
 * The rule this encodes is the only reason the command is worth having: a repository belonging to
 * one of your configured organisations goes under the PRIMARY code base, and everything else goes
 * under the MIRROR. That is what keeps a code directory full of your own work and a mirror full of
 * other people's, which is in turn what makes the picker's default view useful at all. Cloning by
 * hand means deciding it each time, and deciding it wrong is invisible until the picker fills up
 * with third-party checkouts.
 * <p>
 * It existed already, but only as a sequence of JLine prompts - so the one caller who would most
 * benefit from having the rule applied automatically, an agent, was the one caller that could not
 * reach it.
 */
public final class CloneCommands {

    private CloneCommands() { }

    static final java.io.PrintStream OUT = littlejlib.proj.StdoutChannel.data();
    static final java.io.PrintStream ERR = littlejlib.proj.LogConfig.err();
    static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** https, ssh, or the bare org/repo that everyone actually types. */
    static final Pattern
            HTTPS = Pattern.compile("^https?://[^/]+/([^/]+)/([^/]+?)(?:\\.git)?/?$"),
            SSH = Pattern.compile("^(?:ssh://)?git@[^:/]+[:/]([^/]+)/([^/]+?)(?:\\.git)?/?$"),
            SHORT = Pattern.compile("^([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?$");

    public record Target(String org, String repo, String url, Path destination, CodeRepoBaseType base) { }

    @Command(name = "clone", aliases = {"g"},
            description = "Clone a repository into the right code base (configured orgs go to primary, others to the mirror)")
    public static class CloneCmd implements Runnable {

        @Parameters(arity = "1", description = "A GitHub URL, an ssh remote, or just org/repo")
        String repository;

        @Option(names = "--base", description = "Force the destination base: primary, mirror or auxiliary")
        String base;

        @Option(names = "--print", description = "Print where it would go and exit, without cloning")
        boolean dryRun;

        @Option(names = "--json", description = "JSON instead of plain text")
        boolean json;

        @Override
        public void run() {
            var settings = new SettingsManager().loadSettings();
            if (settings == null) {
                ERR.println("no usable settings; run proj once to create them");
                System.exit(4);
                return;
            }

            Target target;
            try {
                target = resolve(repository, base, settings);
            } catch (IllegalArgumentException e) {
                ERR.println(e.getMessage());
                System.exit(2);
                return;
            }

            if (json) {
                var m = new LinkedHashMap<String, Object>();
                m.put("org", target.org());
                m.put("repo", target.repo());
                m.put("url", target.url());
                m.put("base", target.base().slug());
                m.put("destination", target.destination().toString());
                m.put("exists", Files.isDirectory(target.destination()));
                OUT.println(GSON.toJson(m));
            } else {
                OUT.println("org          " + target.org()
                        + (settings.isConfiguredOrganization(target.org()) ? "   (configured)" : "   (external)"));
                OUT.println("repo         " + target.repo());
                OUT.println("url          " + target.url());
                OUT.println("base         " + target.base().slug());
                OUT.println("destination  " + target.destination());
            }

            if (dryRun) return;

            if (Files.isDirectory(target.destination())) {
                ERR.println("already cloned: " + target.destination());
                System.exit(4);
                return;
            }

            try {
                Files.createDirectories(target.destination().getParent());
                // Inherited IO: git's progress goes straight to the caller's terminal, which is what
                // makes a long clone watchable instead of silent.
                var pb = new ProcessBuilder("git", "clone", target.url(), target.destination().toString());
                pb.inheritIO();
                var exit = pb.start().waitFor();
                if (exit != 0) {
                    ERR.println("git clone failed with exit code " + exit);
                    System.exit(exit);
                    return;
                }
                OUT.println("cloned into " + target.destination());
                OUT.println("run 'proj scan' to put it in the index");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.exit(3);
            } catch (Exception e) {
                ERR.println("could not clone: " + e.getMessage());
                System.exit(3);
            }
        }
    }

    /**
     * Works out org, repo and destination. Separated from the command so the rule is testable and
     * so {@code --print} answers with exactly what a real run would do.
     */
    public static Target resolve(String repository, String forcedBase, Settings settings) {
        var input = repository.trim();

        String org = null, repo = null, url = input;
        for (var pattern : java.util.List.of(HTTPS, SSH, SHORT)) {
            var m = pattern.matcher(input);
            if (!m.matches()) continue;
            org = m.group(1);
            repo = m.group(2);
            if (pattern == SHORT) url = "https://github.com/" + org + "/" + repo + ".git";
            break;
        }
        if (org == null) {
            throw new IllegalArgumentException("could not read an org and repo from '" + input + "'. "
                    + "Give a GitHub url, an ssh remote, or org/repo");
        }

        var type = forcedBase == null || forcedBase.isBlank()
                ? (settings.isConfiguredOrganization(org) ? CodeRepoBaseType.PRIMARY : CodeRepoBaseType.GITHUB_MIRROR)
                : CodeRepoBaseType.parse(forcedBase, null);
        if (type == null) {
            throw new IllegalArgumentException("unknown base '" + forcedBase
                    + "'. One of: primary, mirror, auxiliary");
        }

        var baseDir = settings.getCodeRepoBases().stream()
                .filter(b -> b.getType() == type).map(littlejlib.proj.CodeRepoBase::getPath)
                .findFirst().orElse(null);
        if (baseDir == null) {
            throw new IllegalArgumentException("no " + type.slug() + " code base is configured. "
                    + "Add one with: proj config add codebase." + type.slug() + " <directory>");
        }

        return new Target(org, repo, url, Path.of(baseDir, org, repo), type);
    }
}
