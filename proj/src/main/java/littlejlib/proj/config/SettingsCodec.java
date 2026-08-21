package littlejlib.proj.config;

import littlejlib.proj.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings to and from the flat TOML file.
 * <p>
 * FLAT MEANS FLAT: one setting per line, no table headers, no block that has to be read as a whole
 * to understand one value. Repeated things - code bases, IDEs, terminals, organisations - use dotted
 * keys with the name in the key rather than a list of records, so adding a terminal is adding a
 * line and removing one is deleting a line. That is the property that makes a config file editable
 * by hand at two in the morning.
 * <p>
 * The XML carried the same directory twice - once as {@code codeReposPath} and again inside
 * {@code codeRepoBases} - and the two could disagree. Here the bases are the single source of truth
 * and the legacy single-path fields are DERIVED from them on load: the first primary base is the
 * code repos path, the first mirror base is the mirror path. Nothing that reads those fields
 * changes, and there is no longer a way for the file to contradict itself.
 */
public final class SettingsCodec {

    private SettingsCodec() { }

    public static final String
            SCAN_DEPTH = "scanDepth",
            SINGLE_FILE_EDITOR = "singleFileEditor",
            DEFAULT_TERMINAL = "defaultTerminal",
            AGENT_COMMAND = "agentCommand",
            AGENT_SKIP_PERMISSIONS = "agentSkipPermissions",
            AGENT_LOGS_TAIL_PERCENT = "agentLogsTailPercent",
            GLOBAL_HOTKEY = "globalHotkey",
            PRP_TEMPLATE = "prpTemplateSrc",
            STATUS_TEMPLATE = "statusTemplate",
            CODEBASE = "codebase",
            IDE = "ide",
            TERMINAL = "terminal",
            ORG = "org",
            PICKER_COLUMN_ORDER = "pickerColumns.order",
            PICKER_COLUMNS_HIDDEN = "pickerColumns.hidden";

    // ---------- reading ----------

    public static Settings read(String toml) {
        var map = Toml.parse(toml);
        var s = new Settings();

        s.setProjectScanningDepth(Toml.integer(map, SCAN_DEPTH, 4));
        s.setSingleFileEditor(Toml.str(map, SINGLE_FILE_EDITOR));
        s.setDefaultTerminal(Toml.str(map, DEFAULT_TERMINAL));
        s.setAgentCommand(Toml.str(map, AGENT_COMMAND));
        s.setAgentSkipPermissions(Toml.bool(map, AGENT_SKIP_PERMISSIONS, false));
        s.setAgentLogsTailPercent(Toml.integer(map, AGENT_LOGS_TAIL_PERCENT, 10));
        // orKeep, so an absent line means the default rather than "off" - the two are different
        // answers and only one of them is what a file that has never mentioned the key is saying.
        s.setGlobalHotkey(orKeep(Toml.str(map, GLOBAL_HOTKEY), s.getGlobalHotkey()));
        s.setStatusTemplate(orKeep(Toml.str(map, STATUS_TEMPLATE), s.getStatusTemplate()));

        var template = Toml.str(map, PRP_TEMPLATE);
        if (template != null && !template.isBlank()) s.setPrpTemplateSrc(template);

        // codebase.<type> = [ ...paths... ]
        var bases = new ArrayList<CodeRepoBase>();
        for (var typeName : Toml.childrenOf(map, CODEBASE)) {
            var type = CodeRepoBaseType.parse(typeName, null);
            if (type == null) continue;
            for (var path : Toml.list(map, CODEBASE + "." + typeName)) {
                if (path != null && !path.isBlank()) bases.add(new CodeRepoBase(path.trim(), type));
            }
        }
        s.setCodeRepoBases(bases);

        // Derived, so the file cannot hold two disagreeing answers to "where is my code".
        s.setCodeReposPath(firstOfType(bases, CodeRepoBaseType.PRIMARY));
        s.setGitHubMirrorPath(firstOfType(bases, CodeRepoBaseType.GITHUB_MIRROR));

        var ides = new ArrayList<IdeLauncher>();
        for (var name : Toml.childrenOf(map, IDE)) {
            var launcher = new IdeLauncher(name, Toml.str(map, IDE + "." + quoted(name)));
            if (launcher.isUsable()) ides.add(launcher);
        }
        s.setIdeLaunchers(ides);

        // terminal.<name>.open / .run - two lines per terminal, still one setting per line.
        var terminals = new ArrayList<TerminalLauncher>();
        for (var child : Toml.childrenOf(map, TERMINAL)) {
            var cut = child.lastIndexOf('.');
            if (cut < 0) continue;
            var name = child.substring(0, cut);
            if (terminals.stream().anyMatch(t -> name.equals(t.getName()))) continue;
            var t = new TerminalLauncher(name,
                    Toml.str(map, TERMINAL + "." + name + ".open"),
                    Toml.str(map, TERMINAL + "." + name + ".run"));
            if (t.isUsable()) terminals.add(t);
        }
        s.setTerminalLaunchers(terminals);

        var orgs = new ArrayList<Organization>();
        for (var name : Toml.childrenOf(map, ORG)) {
            orgs.add(new Organization(name, Toml.str(map, ORG + "." + quoted(name))));
        }
        s.setOrganizations(orgs);

        s.setPickerColumnOrder(Toml.list(map, PICKER_COLUMN_ORDER));
        s.setPickerHiddenColumns(Toml.list(map, PICKER_COLUMNS_HIDDEN));

        return s;
    }

    /** Keys are stored unquoted in the parsed map, so a lookup uses the raw name. */
    static String quoted(String name) {
        return name;
    }

    static String firstOfType(List<CodeRepoBase> bases, CodeRepoBaseType type) {
        return bases.stream().filter(b -> b.getType() == type)
                .map(CodeRepoBase::getPath).findFirst().orElse(null);
    }

    static String orKeep(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    // ---------- key-addressable access, for the command line ----------

    /**
     * Every setting as the flat key map the file itself uses.
     * <p>
     * Derived by writing and re-reading rather than assembled by hand, so what {@code proj config
     * list} prints can never drift from what the file contains - there is one description of the
     * key space and both go through it.
     */
    public static Map<String, Object> toMap(Settings s) {
        return Toml.parse(write(s));
    }

    /** True when this key holds a list, and so is edited with add/remove rather than set. */
    public static boolean isListKey(String key) {
        return key != null && (key.startsWith(CODEBASE + ".")
                || PICKER_COLUMN_ORDER.equals(key) || PICKER_COLUMNS_HIDDEN.equals(key));
    }

    public static Object get(Settings s, String key) {
        return toMap(s).get(key);
    }

    /**
     * Applies one key. Returns false for a key that is not part of the settings, rather than
     * silently accepting it - a typo that reports success and changes nothing is the worst outcome
     * for a caller that cannot see the file.
     */
    public static boolean set(Settings s, String key, List<String> values) {
        var value = values.isEmpty() ? "" : values.get(0);

        switch (key) {
            case SCAN_DEPTH -> {
                try {
                    s.setProjectScanningDepth(Integer.parseInt(value.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(SCAN_DEPTH + " must be a whole number, not '" + value + "'");
                }
                return true;
            }
            case SINGLE_FILE_EDITOR -> {
                s.setSingleFileEditor(value);
                return true;
            }
            case DEFAULT_TERMINAL -> {
                s.setDefaultTerminal(value);
                return true;
            }
            case AGENT_COMMAND -> {
                s.setAgentCommand(value);
                return true;
            }
            case AGENT_SKIP_PERMISSIONS -> {
                s.setAgentSkipPermissions(Boolean.parseBoolean(value.trim()));
                return true;
            }
            case GLOBAL_HOTKEY -> {
                // Refused rather than accepted silently: a spec this cannot read produces a tool
                // that simply never appears, with nothing anywhere to say why.
                if (!value.isBlank() && !"off".equalsIgnoreCase(value.trim())
                        && littlejlib.proj.util.GlobalHotkey.isOff(value)) {
                    throw new IllegalArgumentException(GLOBAL_HOTKEY + " could not be read as a key: '"
                            + value + "'. Try win+\\ , ctrl+alt+p , scrolllock , or off");
                }
                s.setGlobalHotkey(value.isBlank() ? null : value.trim());
                return true;
            }
            case AGENT_LOGS_TAIL_PERCENT -> {
                try {
                    s.setAgentLogsTailPercent(Integer.parseInt(value.trim()));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(AGENT_LOGS_TAIL_PERCENT
                            + " must be a whole number of percent, not '" + value + "'");
                }
                return true;
            }
            case PRP_TEMPLATE -> {
                s.setPrpTemplateSrc(value);
                return true;
            }
            case STATUS_TEMPLATE -> {
                s.setStatusTemplate(value);
                return true;
            }
            // Whole-list assignment. These are list keys, so add/drop are the usual way in, but
            // "set" has to replace outright - that is how the picker writes a reordered layout.
            case PICKER_COLUMN_ORDER -> {
                s.setPickerColumnOrder(cleaned(values));
                return true;
            }
            case PICKER_COLUMNS_HIDDEN -> {
                s.setPickerHiddenColumns(cleaned(values));
                return true;
            }
            default -> { }
        }

        if (key.startsWith(CODEBASE + ".")) {
            var type = CodeRepoBaseType.parse(key.substring(CODEBASE.length() + 1), null);
            if (type == null) return false;
            var kept = new ArrayList<>(s.getCodeRepoBases().stream()
                    .filter(b -> b.getType() != type).toList());
            values.stream().filter(v -> v != null && !v.isBlank())
                    .forEach(v -> kept.add(new CodeRepoBase(v.trim(), type)));
            s.setCodeRepoBases(kept);
            return true;
        }

        if (key.startsWith(IDE + ".")) {
            var name = key.substring(IDE.length() + 1);
            if (name.isBlank()) return false;
            var list = new ArrayList<>(s.getIdeLaunchers());
            list.removeIf(l -> name.equalsIgnoreCase(l.getName()));
            list.add(new IdeLauncher(name, value));
            s.setIdeLaunchers(list);
            return true;
        }

        if (key.startsWith(ORG + ".")) {
            var name = key.substring(ORG.length() + 1);
            if (name.isBlank()) return false;
            var list = new ArrayList<>(s.getOrganizations());
            list.removeIf(o -> name.equalsIgnoreCase(o.getName()));
            list.add(new Organization(name, value));
            s.setOrganizations(list);
            return true;
        }

        if (key.startsWith(TERMINAL + ".")) {
            var rest = key.substring(TERMINAL.length() + 1);
            var cut = rest.lastIndexOf('.');
            if (cut < 0) return false;
            // Split on the LAST dot, so a terminal named "Terminal.app" keeps its dot.
            var name = rest.substring(0, cut);
            var field = rest.substring(cut + 1);
            if (name.isBlank()) return false;

            var list = new ArrayList<>(s.getTerminalLaunchers());
            var existing = list.stream().filter(t -> name.equalsIgnoreCase(t.getName())).findFirst();
            var t = existing.orElseGet(() -> {
                var created = new TerminalLauncher(name, "", "");
                list.add(created);
                return created;
            });
            switch (field) {
                case "open" -> t.setOpenCommand(value);
                case "run" -> t.setRunCommand(value);
                default -> {
                    return false;
                }
            }
            s.setTerminalLaunchers(list);
            return true;
        }

        return false;
    }

    /** Removes a key, or a whole named entry when given {@code terminal.NAME} / {@code ide.NAME}. */
    public static boolean unset(Settings s, String key) {
        switch (key) {
            case SINGLE_FILE_EDITOR, DEFAULT_TERMINAL, AGENT_COMMAND, PRP_TEMPLATE, STATUS_TEMPLATE ->
                    { return set(s, key, List.of("")); }
            case SCAN_DEPTH -> {
                s.setProjectScanningDepth(4);
                return true;
            }
            case AGENT_SKIP_PERMISSIONS -> {
                s.setAgentSkipPermissions(false);
                return true;
            }
            case AGENT_LOGS_TAIL_PERCENT -> {
                s.setAgentLogsTailPercent(10);
                return true;
            }
            case GLOBAL_HOTKEY -> {
                s.setGlobalHotkey(littlejlib.proj.util.GlobalHotkey.DEFAULT);
                return true;
            }
            // Empty is meaningful here: it restores the built-in order and the built-in visibility
            // rather than leaving the picker with no columns.
            case PICKER_COLUMN_ORDER, PICKER_COLUMNS_HIDDEN -> { return set(s, key, List.of()); }
            default -> { }
        }

        if (key.startsWith(CODEBASE + ".")) return set(s, key, List.of());

        if (key.startsWith(IDE + ".")) {
            var name = key.substring(IDE.length() + 1);
            var list = new ArrayList<>(s.getIdeLaunchers());
            var removed = list.removeIf(l -> name.equalsIgnoreCase(l.getName()));
            s.setIdeLaunchers(list);
            return removed;
        }

        if (key.startsWith(ORG + ".")) {
            var name = key.substring(ORG.length() + 1);
            var list = new ArrayList<>(s.getOrganizations());
            var removed = list.removeIf(o -> name.equalsIgnoreCase(o.getName()));
            s.setOrganizations(list);
            return removed;
        }

        if (key.startsWith(TERMINAL + ".")) {
            var rest = key.substring(TERMINAL.length() + 1);
            if (rest.endsWith(".open") || rest.endsWith(".run")) return set(s, key, List.of(""));
            var list = new ArrayList<>(s.getTerminalLaunchers());
            var removed = list.removeIf(t -> rest.equalsIgnoreCase(t.getName()));
            s.setTerminalLaunchers(list);
            return removed;
        }

        return false;
    }

    /** Appends to a list key, ignoring a value that is already there. */
    public static boolean addTo(Settings s, String key, List<String> values) {
        if (!isListKey(key)) return false;

        if (PICKER_COLUMN_ORDER.equals(key) || PICKER_COLUMNS_HIDDEN.equals(key)) {
            var list = new ArrayList<>(PICKER_COLUMN_ORDER.equals(key)
                    ? s.getPickerColumnOrder() : s.getPickerHiddenColumns());
            for (var v : cleaned(values)) if (!list.contains(v)) list.add(v);
            return set(s, key, list);
        }

        var type = CodeRepoBaseType.parse(key.substring(CODEBASE.length() + 1), null);
        if (type == null) return false;

        var list = new ArrayList<>(s.getCodeRepoBases());
        for (var v : values) {
            if (v == null || v.isBlank()) continue;
            var path = v.trim();
            if (list.stream().anyMatch(b -> b.sameLocationAs(java.nio.file.Paths.get(path)))) {
                // Retype rather than duplicate: the same directory cannot be two kinds of base.
                list.stream().filter(b -> b.sameLocationAs(java.nio.file.Paths.get(path)))
                        .forEach(b -> b.setType(type));
            } else {
                list.add(new CodeRepoBase(path, type));
            }
        }
        s.setCodeRepoBases(list);
        return true;
    }

    public static boolean removeFrom(Settings s, String key, List<String> values) {
        if (!isListKey(key)) return false;

        if (PICKER_COLUMN_ORDER.equals(key) || PICKER_COLUMNS_HIDDEN.equals(key)) {
            var list = new ArrayList<>(PICKER_COLUMN_ORDER.equals(key)
                    ? s.getPickerColumnOrder() : s.getPickerHiddenColumns());
            var dropped = list.removeAll(cleaned(values));
            set(s, key, list);
            return dropped;
        }

        var list = new ArrayList<>(s.getCodeRepoBases());
        var removed = false;
        for (var v : values) {
            if (v == null || v.isBlank()) continue;
            removed |= list.removeIf(b -> b.sameLocationAs(java.nio.file.Paths.get(v.trim())));
        }
        s.setCodeRepoBases(list);
        return removed;
    }

    // ---------- writing ----------

    public static String write(Settings s) {
        var out = new StringBuilder();

        out.append("# Project Build & Dev Tools - settings\n");
        out.append("#\n");
        out.append("# Flat TOML: one setting per line, no sections to keep in your head.\n");
        out.append("# Values in 'single quotes' are LITERAL - backslashes and quotes inside them mean\n");
        out.append("# exactly what they say, so a Windows path is written once, as itself.\n");
        out.append("# A value that itself contains a single quote is wrapped in '''three''' instead.\n");
        out.append("#\n");
        out.append("# Edited by 'proj settings'. Hand edits are fine and are preserved on the next save,\n");
        out.append("# except for comments you add, which a rewrite does not keep.\n");
        out.append("\n");

        out.append("# How many directory levels below a code base the scanner descends.\n");
        out.append(Toml.line(Toml.dotted(SCAN_DEPTH), (long) s.getProjectScanningDepth())).append('\n');
        out.append('\n');

        out.append("# Editor for single files - PRPs, poms, this file. Blank means 'whatever is installed'.\n");
        out.append(Toml.line(Toml.dotted(SINGLE_FILE_EDITOR), nz(s.getSingleFileEditor()))).append('\n');
        out.append('\n');

        out.append("# ---- code bases -------------------------------------------------------------\n");
        out.append("# Directories that hold many repositories.\n");
        out.append("#   primary   = your own code. Shown by default, and watched for live changes.\n");
        out.append("#   mirror    = the GitHub clone cache behind 'proj g'.\n");
        out.append("#   auxiliary = archives and other code you keep but rarely open.\n");
        out.append("# All three are indexed and searchable; mirror and auxiliary are simply not shown\n");
        out.append("# until you tick their box in the picker. Move a path between lines to retype it.\n");
        for (var type : CodeRepoBaseType.values()) {
            var paths = s.getCodeRepoBases().stream()
                    .filter(b -> b.getType() == type).map(CodeRepoBase::getPath).toList();
            out.append(Toml.line(Toml.dotted(CODEBASE, type.slug()), paths)).append('\n');
        }
        out.append('\n');

        out.append("# ---- terminals --------------------------------------------------------------\n");
        out.append("# %PATH% is the directory. %SCRIPT% is a generated script that changes into that\n");
        out.append("# directory and then runs the command; it exists because some terminals cannot be\n");
        out.append("# told to run something somewhere in one go. An empty 'run' means that terminal\n");
        out.append("# can open a shell but cannot start a command, and agents will use another one.\n");
        var terminals = s.getTerminalLaunchers().stream().filter(TerminalLauncher::isUsable).toList();
        if (terminals.isEmpty()) {
            out.append("# (none registered, so whatever is installed on this machine is used -\n");
            out.append("#  'proj term --which' says which, and Settings has a Detect button)\n");
        }
        for (var t : terminals) {
            out.append(Toml.line(Toml.dotted(TERMINAL, t.getName(), "open"), nz(t.getOpenCommand()))).append('\n');
            out.append(Toml.line(Toml.dotted(TERMINAL, t.getName(), "run"), nz(t.getRunCommand()))).append('\n');
        }
        out.append("# Which one to use. Blank means the best one detected on this machine.\n");
        out.append(Toml.line(Toml.dotted(DEFAULT_TERMINAL), nz(s.getDefaultTerminal()))).append('\n');
        out.append('\n');

        out.append("# The key that summons the picker from anywhere, while the daemon is running.\n");
        out.append("# Windows is asked to CLAIM it, so nothing else sees it and nothing double-fires -\n");
        out.append("# which also means a combination already owned by something else simply fails to\n");
        out.append("# register, and the daemon log says so on startup.\n");
        out.append("#   win+\\ | win+` | win+f12 | ctrl+alt+p | scrolllock | pause | menu | off\n");
        out.append("# A bare key with no modifier is allowed and is the kindest on the hands: claiming\n");
        out.append("# scrolllock or menu also suppresses whatever they would otherwise have done.\n");
        out.append(Toml.line(Toml.dotted(GLOBAL_HOTKEY),
                s.getGlobalHotkey() == null || s.getGlobalHotkey().isBlank()
                        ? littlejlib.proj.util.GlobalHotkey.DEFAULT : s.getGlobalHotkey()))
           .append('\n');
        out.append('\n');

        out.append("# ---- editors and IDEs -------------------------------------------------------\n");
        out.append("# One 'open in' button per line in the picker. %PATH% is the project directory.\n");
        out.append("# Leave these out entirely and the picker offers whatever it detects.\n");
        for (var l : s.getIdeLaunchers()) {
            if (!l.isUsable()) continue;
            out.append(Toml.line(Toml.dotted(IDE, l.getName()), l.getCommand())).append('\n');
        }
        out.append('\n');

        out.append("# ---- PRPs and the coding agent ----------------------------------------------\n");
        out.append("# How to start a coding agent on a PRP. The working directory is always the\n");
        out.append("# PROJECT ROOT, never the prp folder.\n");
        out.append("#   %PROMPT%   the instruction, assembled from what is on disk: the PRP, its\n");
        out.append("#              status file if it has one, and how much of the last session's\n");
        out.append("#              log to read. Each part appears only if that file exists.\n");
        out.append("#   %FLAGS%    the flags below, or nothing\n");
        out.append("#   %PRP%      the PRP path relative to the project, e.g. prp/14-prp-name.md\n");
        out.append("#   %PRP_ABS%  its absolute path\n");
        out.append("#   %PATH%     the project directory\n");
        // Written out even when unset. Blank behaves as the default anyway, and a file that shows
        // the command is one you can edit; a file showing '' is one you have to go and look up.
        //
        // templateOf, not the raw value: a file still carrying the OLD default is carrying a string
        // nobody chose, and the application is already ignoring it. Writing the effective template
        // instead migrates the file on the next save, so what it says and what runs are the same
        // thing - which is the whole reason for writing it out at all.
        out.append(Toml.line(Toml.dotted(AGENT_COMMAND),
                littlejlib.proj.prp.AgentLauncher.templateOf(s))).append('\n');
        out.append('\n');
        out.append("# Run the agent with no permission prompts at all. OFF by default, and it should\n");
        out.append("# stay off unless you mean it: it gives the agent the machine. Turn it on here or\n");
        out.append("# in Settings, 'PRP and agent'.\n");
        out.append(Toml.line(Toml.dotted(AGENT_SKIP_PERMISSIONS), s.isAgentSkipPermissions())).append('\n');
        out.append('\n');
        out.append("# How much of the PREVIOUS session's log the agent is told to read, from the end.\n");
        out.append("# 0 means do not mention it at all. Only ever added when such a log exists.\n");
        out.append(Toml.line(Toml.dotted(AGENT_LOGS_TAIL_PERCENT), (long) s.getAgentLogsTailPercent()))
           .append('\n');
        out.append('\n');
        out.append("# Where a new PRP's text comes from. 'bundled' is the copy inside proj itself and\n");
        out.append("# needs no network; an http(s) URL or a local file path overrides it.\n");
        // effectiveSrc, not the raw value, for the same reason as agentCommand above: a file
        // carrying the GitHub Pages URL of an organisation this project left two moves ago has not
        // chosen anything, and is already being ignored. Writing the effective value migrates it.
        out.append(Toml.line(Toml.dotted(PRP_TEMPLATE),
                PRPTemplateLoader.effectiveSrc(s.getPrpTemplateSrc()))).append('\n');
        out.append('\n');

        out.append("# ---- organisations ----------------------------------------------------------\n");
        out.append("# Directory name under a code base -> the maven groupId its projects publish under.\n");
        for (var o : s.getOrganizations()) {
            if (o.getName() == null || o.getName().isBlank()) continue;
            out.append(Toml.line(Toml.dotted(ORG, o.getName()), nz(o.getGroupId()))).append('\n');
        }
        out.append('\n');

        out.append("# ---- project picker columns -------------------------------------------------\n");
        out.append("# Which columns the picker shows and in what order, by id. Both are written for\n");
        out.append("# you when you drag a column header or untick one in the table's own menu, so\n");
        out.append("# editing them here is only for putting things back.\n");
        out.append("# An EMPTY order means the built-in order; an empty hidden list means each\n");
        out.append("# column's own default. Neither empty list means 'show nothing'.\n");
        out.append("# Known ids: ").append(String.join(", ", littlejlib.proj.ui.PickerColumn.allIds())).append('\n');
        // Split into segments: dotted() quotes any segment that is not bare, and handed the whole
        // dotted key as one segment it would write 'pickerColumns.order' - a single key with a dot
        // in its name, which parses cleanly and is not the key anything reads.
        out.append(Toml.line(Toml.dotted(PICKER_COLUMN_ORDER.split("\\.")), s.getPickerColumnOrder())).append('\n');
        out.append(Toml.line(Toml.dotted(PICKER_COLUMNS_HIDDEN.split("\\.")), s.getPickerHiddenColumns())).append('\n');
        out.append('\n');

        out.append("# ---- PRP status template ----------------------------------------------------\n");
        out.append(Toml.line(Toml.dotted(STATUS_TEMPLATE), nz(s.getStatusTemplate()))).append('\n');

        return out.toString();
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Trimmed, blanks dropped - a list key written by hand tends to arrive with stray spaces. */
    static List<String> cleaned(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }
}
