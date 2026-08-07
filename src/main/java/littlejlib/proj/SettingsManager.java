package littlejlib.proj;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import littlejlib.proj.config.SettingsCodec;
import littlejlib.proj.util.AppPaths;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and saves {@code Settings.toml}.
 * <p>
 * It used to be XML, and the reason it is not any more is escaping. Almost every value here is a
 * Windows path or a command template - backslashes, percent signs, single and double quotes - and
 * XML made each of those a small encoding decision, on top of a nested shape where the same
 * directory appeared twice and the two copies could disagree. TOML literal strings take the value
 * exactly as written, and a flat file has nowhere for a contradiction to hide.
 * <p>
 * An existing Settings.xml is MIGRATED once, automatically, and kept as {@code Settings.xml.migrated}
 * rather than deleted - a config migration that is also the only copy of the config is not a
 * migration anyone should have to trust.
 */
public class SettingsManager {

    private static final String SETTINGS_FILE = "Settings.toml";
    private static final String LEGACY_FILE = "Settings.xml";

    private Path getSettingsPath() {
        return AppPaths.settings();
    }

    private Path getLegacyPath() {
        return AppPaths.legacySettings();
    }

    public Settings loadSettings() {
        var path = getSettingsPath();

        if (Files.exists(path)) {
            try {
                return SettingsCodec.read(Files.readString(path, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                // A parse error names its line. Returning null here would send the caller into
                // first-run setup and overwrite a file that just has a typo in it.
                System.err.println("Could not read " + path + ": " + e.getMessage());
                System.err.println("Fix that line, or delete the file to start again from a template.");
                return null;
            } catch (Exception e) {
                System.err.println("Error loading settings: " + e.getMessage());
                return null;
            }
        }

        var legacy = getLegacyPath();
        if (Files.exists(legacy)) {
            var migrated = migrateFromXml(legacy);
            if (migrated != null) return migrated;
        }

        createTemplateSettings();
        return null;
    }

    public void saveSettings(Settings settings) {
        try {
            var path = getSettingsPath();
            Files.createDirectories(path.getParent());
            // Written to a sibling and moved into place, so an interrupted save cannot leave a
            // half-written settings file where a whole one used to be.
            var tmp = path.resolveSibling(SETTINGS_FILE + ".tmp");
            Files.writeString(tmp, SettingsCodec.write(settings), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("Error saving settings: " + e.getMessage());
        }
    }

    public Path getSettingsFilePath() {
        return getSettingsPath();
    }

    public boolean isConfigured() {
        var settings = loadSettings();
        return settings != null
                && settings.getCodeReposPath() != null && !settings.getCodeReposPath().isEmpty()
                && !settings.getOrganizations().isEmpty();
    }

    // ---------- migration ----------

    /**
     * Reads the old XML once, writes the TOML, and renames the XML aside. Returns null if the XML
     * could not be read, in which case nothing is written and the old file is left alone.
     */
    private Settings migrateFromXml(Path legacy) {
        try {
            var settings = readLegacyXml(legacy);
            if (settings == null) return null;

            // The legacy singular ideLauncher becomes a named entry, so the migration does not
            // quietly drop the one IDE button an older install actually had.
            if (settings.getIdeLaunchers().isEmpty()
                    && settings.getIdeLauncher() != null && !settings.getIdeLauncher().isBlank()) {
                var launchers = new ArrayList<IdeLauncher>();
                launchers.add(new IdeLauncher(nameFromCommand(settings.getIdeLauncher()),
                        settings.getIdeLauncher()));
                settings.setIdeLaunchers(launchers);
            }

            saveSettings(settings);
            var kept = legacy.resolveSibling(LEGACY_FILE + ".migrated");
            Files.move(legacy, kept, StandardCopyOption.REPLACE_EXISTING);

            System.err.println("Settings migrated to " + getSettingsPath());
            System.err.println("The old file is kept as " + kept);
            return settings;
        } catch (Exception e) {
            System.err.println("Could not migrate " + legacy + ": " + e.getMessage());
            return null;
        }
    }

    /** "netbeans64.exe --open %PATH%" -> "netbeans64", which is a name a person recognises. */
    static String nameFromCommand(String command) {
        var parts = littlejlib.proj.util.Cmd.parse(command);
        if (parts.length == 0) return "IDE";
        var exe = Paths.get(parts[0]).getFileName();
        if (exe == null) return "IDE";
        var name = exe.toString();
        var dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private Settings readLegacyXml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(path.toFile());
        doc.getDocumentElement().normalize();

        var settings = new Settings();

        var codeReposNodes = doc.getElementsByTagName("codeReposPath");
        if (codeReposNodes.getLength() > 0) {
            var el = (Element) codeReposNodes.item(0);
            settings.setCodeReposPath(el.getTextContent());
            var depthAttr = el.getAttribute("projectScanningDepth");
            if (depthAttr != null && !depthAttr.isEmpty()) {
                try {
                    settings.setProjectScanningDepth(Integer.parseInt(depthAttr));
                } catch (NumberFormatException e) {
                    // keep the default
                }
            }
        }

        settings.setGitHubMirrorPath(getElementText(doc, "gitHubMirrorPath"));
        settings.setSingleFileEditor(getElementText(doc, "singleFileEditor"));
        settings.setIdeLauncher(getElementText(doc, "ideLauncher"));
        settings.setDefaultTerminal(getElementText(doc, "defaultTerminal"));
        settings.setAgentCommand(getElementText(doc, "agentCommand"));

        var baseNodes = doc.getElementsByTagName("codeRepoBase");
        var bases = new ArrayList<CodeRepoBase>();
        for (var i = 0; i < baseNodes.getLength(); i++) {
            var el = (Element) baseNodes.item(i);
            var p = el.getTextContent();
            if (p == null || p.isBlank()) continue;
            bases.add(new CodeRepoBase(p.trim(),
                    CodeRepoBaseType.parse(el.getAttribute("type"), CodeRepoBaseType.AUXILIARY)));
        }
        settings.setCodeRepoBases(bases);

        var extraPaths = doc.getElementsByTagName("additionalCodeReposPath");
        var extras = new ArrayList<String>();
        for (var i = 0; i < extraPaths.getLength(); i++) {
            var p = extraPaths.item(i).getTextContent();
            if (p != null && !p.isBlank()) extras.add(p.trim());
        }
        settings.setAdditionalCodeReposPaths(extras);

        var launcherList = doc.getElementsByTagName("launcher");
        var launchers = new ArrayList<IdeLauncher>();
        for (var i = 0; i < launcherList.getLength(); i++) {
            var el = (Element) launcherList.item(i);
            var l = new IdeLauncher(el.getAttribute("name"), el.getTextContent());
            if (l.isUsable()) launchers.add(l);
        }
        settings.setIdeLaunchers(launchers);

        var terminalList = doc.getElementsByTagName("terminal");
        var terminals = new ArrayList<TerminalLauncher>();
        for (var i = 0; i < terminalList.getLength(); i++) {
            var el = (Element) terminalList.item(i);
            var t = new TerminalLauncher(el.getAttribute("name"),
                    getChildElementText(el, "open"), getChildElementText(el, "run"));
            if (t.isUsable()) terminals.add(t);
        }
        settings.setTerminalLaunchers(terminals);

        var orgList = doc.getElementsByTagName("organization");
        var organizations = new ArrayList<Organization>();
        for (var i = 0; i < orgList.getLength(); i++) {
            var node = orgList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            var orgElement = (Element) node;
            var name = getChildElementText(orgElement, "name");
            var groupId = getChildElementText(orgElement, "groupId");
            if (name != null && !name.isEmpty()) organizations.add(new Organization(name, groupId));
        }
        settings.setOrganizations(organizations);

        var prpTemplateNodes = doc.getElementsByTagName("prpTemplate");
        if (prpTemplateNodes.getLength() > 0) {
            var src = ((Element) prpTemplateNodes.item(0)).getAttribute("src");
            if (src != null && !src.isEmpty()) settings.setPrpTemplateSrc(src);
        }

        var statusTemplate = getElementText(doc, "statusTemplate");
        if (statusTemplate != null && !statusTemplate.isEmpty()) settings.setStatusTemplate(statusTemplate);

        // The bases are the source of truth from here on, so a file that only had the old single
        // paths gets them materialised rather than left to be re-derived every load.
        if (settings.getExplicitCodeRepoBases().isEmpty()) {
            settings.setCodeRepoBases(new ArrayList<>(settings.getCodeRepoBases()));
        }
        return settings;
    }

    private String getElementText(Document doc, String tagName) {
        var nodeList = doc.getElementsByTagName(tagName);
        return nodeList.getLength() > 0 ? nodeList.item(0).getTextContent() : null;
    }

    private String getChildElementText(Element parent, String tagName) {
        var nodeList = parent.getElementsByTagName(tagName);
        return nodeList.getLength() > 0 ? nodeList.item(0).getTextContent() : null;
    }

    // ---------- first run ----------

    private void createTemplateSettings() {
        try {
            var path = getSettingsPath();
            Files.createDirectories(path.getParent());

            var windows = System.getProperty("os.name", "").toLowerCase().contains("win");
            var home = System.getProperty("user.home");
            var codePath = windows ? "C:\\Users\\YourName\\code" : home + "/code";
            var mirrorPath = windows ? "C:\\Users\\YourName\\github-mirror" : home + "/github-mirror";

            var template = new Settings();
            template.setProjectScanningDepth(4);
            template.setSingleFileEditor(windows ? "notepad++.exe" : "nano");
            template.setAgentCommand(littlejlib.proj.prp.AgentLauncher.DEFAULT_AGENT_COMMAND);
            template.setCodeRepoBases(List.of(
                    new CodeRepoBase(codePath, CodeRepoBaseType.PRIMARY),
                    new CodeRepoBase(mirrorPath, CodeRepoBaseType.GITHUB_MIRROR)));
            template.setOrganizations(List.of(new Organization("your-github-org", "io.github.your-github-org")));
            // Whatever is actually installed beats an example that names software you do not have.
            template.setTerminalLaunchers(
                    littlejlib.proj.terminal.TerminalDetector.detect());
            template.setIdeLaunchers(
                    littlejlib.proj.ide.IdeDetector.detect().stream()
                            .filter(i -> i.opensFolders)
                            .map(i -> new IdeLauncher(i.name, i.command())).toList());

            var body = "# FIRST RUN. This is a template: edit the paths below to match this machine,\n"
                    + "# then run proj again. 'proj settings' opens a window that edits this file.\n"
                    + "\n"
                    + SettingsCodec.write(template);

            Files.writeString(path, body, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Error creating template settings: " + e.getMessage());
        }
    }
}
