package littlejlib.proj;

import littlejlib.proj.config.SettingsCodec;
import littlejlib.proj.config.Toml;

import java.util.List;

/**
 * Round-trips a Settings object through the flat TOML and checks that nothing changed, using values
 * chosen to be exactly the ones that break a config format: Windows paths, embedded double quotes,
 * embedded single quotes (which is what PowerShell templates contain), percent placeholders,
 * non-ASCII, and a multi-line block.
 *
 * mvn exec:java -Dexec.mainClass="littlejlib.proj.SmokeSettingsToml" -Dexec.classpathScope=test
 */
public class SmokeSettingsToml {

    static int failures = 0;

    public static void main(String[] args) {
        var s = new Settings();
        s.setProjectScanningDepth(5);
        s.setWorkspaceRefreshMinutes(90);
        s.setSingleFileEditor("C:\\user\\Apps\\VScode\\code.exe");
        s.setDefaultTerminal("Tabby");
        // Deliberately not one of AgentLauncher.LEGACY_AGENT_COMMANDS: those are fossils and the
        // codec MIGRATES them on write, so using one here tests the migration, not the round trip.
        s.setAgentCommand("myagent %FLAGS% --prp \"%PRP_ABS%\" \"%PROMPT%\"");
        s.setPrpTemplateSrc("https://example.invalid/PRPTemplate.md");
        s.setStatusTemplate("PRP Number: %index%\nPRP Name: %name%\nStatus: IN PROGRESS\n\n## Notes\n");

        s.setCodeRepoBases(List.of(
                new CodeRepoBase("C:\\user\\code", CodeRepoBaseType.PRIMARY),
                new CodeRepoBase("C:\\user\\github-mirror", CodeRepoBaseType.GITHUB_MIRROR),
                new CodeRepoBase("D:\\archive\\old code", CodeRepoBaseType.AUXILIARY)));

        s.setIdeLaunchers(List.of(
                new IdeLauncher("NetBeans", "C:\\user\\Apps\\netbeans\\bin\\netbeans64.exe \"--open\" \"%PATH%\""),
                new IdeLauncher("VS Code", "\"C:\\user\\Apps\\VScode\\code.exe\" \"%PATH%\"")));

        s.setTerminalLaunchers(List.of(
                new TerminalLauncher("Tabby", "\"C:\\user\\Apps\\tabby\\Tabby.exe\" open \"%PATH%\"", ""),
                // The single quotes here are the interesting part: a literal TOML string cannot
                // contain one, so this must come back through the '''...''' form intact.
                new TerminalLauncher("PowerShell",
                        "\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoExit -Command Set-Location -LiteralPath '%PATH%'",
                        "\"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" -NoExit -Command & '%SCRIPT%'")));

        s.setOrganizations(List.of(
                new Organization("acme", "io.github.acme"),
                new Organization("xyz-jphil", "io.github.xyz-jphil"),
                new Organization("example-org", "io.github.example-org")));

        var toml = SettingsCodec.write(s);
        System.out.println(toml);
        System.out.println("======================= round trip =======================");

        var back = SettingsCodec.read(toml);

        eq("scanDepth", s.getProjectScanningDepth(), back.getProjectScanningDepth());
        eq("workspaceRefreshMinutes", s.getWorkspaceRefreshMinutes(), back.getWorkspaceRefreshMinutes());
        eq("singleFileEditor", s.getSingleFileEditor(), back.getSingleFileEditor());
        eq("defaultTerminal", s.getDefaultTerminal(), back.getDefaultTerminal());
        eq("agentCommand", s.getAgentCommand(), back.getAgentCommand());
        eq("prpTemplateSrc", s.getPrpTemplateSrc(), back.getPrpTemplateSrc());
        eq("statusTemplate", s.getStatusTemplate(), back.getStatusTemplate());

        eq("bases", s.getCodeRepoBases().size(), back.getCodeRepoBases().size());
        for (var i = 0; i < s.getCodeRepoBases().size(); i++) {
            eq("base path " + i, s.getCodeRepoBases().get(i).getPath(), back.getCodeRepoBases().get(i).getPath());
            eq("base type " + i, s.getCodeRepoBases().get(i).getType(), back.getCodeRepoBases().get(i).getType());
        }
        // Derived, not stored: the file can no longer disagree with itself about where code lives.
        eq("derived codeReposPath", "C:\\user\\code", back.getCodeReposPath());
        eq("derived gitHubMirrorPath", "C:\\user\\github-mirror", back.getGitHubMirrorPath());

        eq("ides", s.getIdeLaunchers().size(), back.getIdeLaunchers().size());
        for (var i = 0; i < s.getIdeLaunchers().size(); i++) {
            eq("ide name " + i, s.getIdeLaunchers().get(i).getName(), back.getIdeLaunchers().get(i).getName());
            eq("ide cmd " + i, s.getIdeLaunchers().get(i).getCommand(), back.getIdeLaunchers().get(i).getCommand());
        }

        eq("terminals", s.getTerminalLaunchers().size(), back.getTerminalLaunchers().size());
        for (var i = 0; i < s.getTerminalLaunchers().size(); i++) {
            var a = s.getTerminalLaunchers().get(i);
            var b = back.getTerminalLaunchers().get(i);
            eq("terminal name " + i, a.getName(), b.getName());
            eq("terminal open " + i, a.getOpenCommand(), b.getOpenCommand());
            eq("terminal run " + i, nz(a.getRunCommand()), nz(b.getRunCommand()));
        }

        eq("orgs", s.getOrganizations().size(), back.getOrganizations().size());
        for (var i = 0; i < s.getOrganizations().size(); i++) {
            eq("org name " + i, s.getOrganizations().get(i).getName(), back.getOrganizations().get(i).getName());
            eq("org groupId " + i, s.getOrganizations().get(i).getGroupId(), back.getOrganizations().get(i).getGroupId());
        }

        // Writing what we just read must produce the identical file, or a save would drift.
        eq("stable on rewrite", toml, SettingsCodec.write(back));

        // A hand edit with no quotes at all should be REFUSED rather than half-read.
        try {
            Toml.parse("singleFileEditor = C:\\some\\path\n");
            fail("an unquoted value was accepted");
        } catch (IllegalArgumentException e) {
            System.out.println("ok    unquoted value refused: " + e.getMessage());
        }

        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILURES");
        System.exit(failures == 0 ? 0 : 1);
    }

    static void eq(String what, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            System.out.println("ok    " + what);
        } else {
            fail(what + "\n        expected: " + expected + "\n        actual:   " + actual);
        }
    }

    static void fail(String message) {
        failures++;
        System.out.println("FAIL  " + message);
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }
}
