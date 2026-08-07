package littlejlib.proj;

import java.util.ArrayList;
import java.util.List;

public class Settings {
    private String codeReposPath;
    private String gitHubMirrorPath; // Where arbitrary cloned GitHub repos are stored (falls back to codeReposPath)
    private int projectScanningDepth = 4; // Default depth for scanning project directories
    private String singleFileEditor; // Editor for single files (PRPs, settings, etc.)
    private String ideLauncher; // IDE command template for opening projects (%PATH% placeholder)
    private List<String> additionalCodeReposPaths; // extra roots the picker browses, mm's codedir2..N
    private List<CodeRepoBase> codeRepoBases;     // explicit coderepobases; overrides the derived set
    private List<IdeLauncher> ideLaunchers; // per-row "open in X" columns in the project picker
    private List<TerminalLauncher> terminalLaunchers; // registered terminals; detection fills the gap
    private String defaultTerminal;  // which of them to use, by name; blank means best detected
    private String agentCommand;     // how to start a coding agent on a PRP; %PRP% is relative to the project
    private List<Organization> organizations;
    private String prpTemplateSrc; // URL or local path to PRP template file
    private String statusTemplate;
    private List<String> pickerColumnOrder;   // PickerColumn ids, left to right; empty means the built-in order
    private List<String> pickerHiddenColumns; // PickerColumn ids to start hidden; empty means each column's default

    public Settings() {
        this.additionalCodeReposPaths = new ArrayList<>();
        this.codeRepoBases = new ArrayList<>();
        this.ideLaunchers = new ArrayList<>();
        this.terminalLaunchers = new ArrayList<>();
        this.organizations = new ArrayList<>();
        this.pickerColumnOrder = new ArrayList<>();
        this.pickerHiddenColumns = new ArrayList<>();
        this.prpTemplateSrc = PRPTemplateLoader.DEFAULT_TEMPLATE_SRC; // Default GitHub Pages URL
        this.statusTemplate = "PRP Number: %index%\n" +
                "PRP Name: %name%\n" +
                "Status: IN PROGRESS\n\n" +
                "## Implementation Notes\n\n" +
                "## Completed Tasks\n\n" +
                "## Pending Tasks\n\n";
    }

    public String getCodeReposPath() {
        return codeReposPath;
    }

    public void setCodeReposPath(String codeReposPath) {
        this.codeReposPath = codeReposPath;
    }

    public String getGitHubMirrorPath() {
        return gitHubMirrorPath;
    }

    public void setGitHubMirrorPath(String gitHubMirrorPath) {
        this.gitHubMirrorPath = gitHubMirrorPath;
    }

    /**
     * Mirror base for arbitrary (external) cloned GitHub repos. Uses gitHubMirrorPath
     * when configured, otherwise falls back to codeReposPath for compatibility.
     */
    public String getGitCloneBasePath() {
        return (gitHubMirrorPath != null && !gitHubMirrorPath.trim().isEmpty())
                ? gitHubMirrorPath : codeReposPath;
    }

    /** True if the org matches one of the configured (internal) organizations. */
    public boolean isConfiguredOrganization(String orgName) {
        if (orgName == null) return false;
        return organizations.stream().anyMatch(o -> orgName.equalsIgnoreCase(o.getName()));
    }

    /**
     * Clone destination base for a given org: configured (internal) orgs go into
     * codeReposPath; everything else is treated as external and goes into the mirror.
     */
    public String getCloneBasePathForOrg(String orgName) {
        return isConfiguredOrganization(orgName) ? codeReposPath : getGitCloneBasePath();
    }

    public String getSingleFileEditor() {
        return singleFileEditor;
    }

    public void setSingleFileEditor(String singleFileEditor) {
        this.singleFileEditor = singleFileEditor;
    }

    public String getIdeLauncher() {
        return ideLauncher;
    }

    public void setIdeLauncher(String ideLauncher) {
        this.ideLauncher = ideLauncher;
    }

    public List<String> getAdditionalCodeReposPaths() {
        return additionalCodeReposPaths;
    }

    public void setAdditionalCodeReposPaths(List<String> paths) {
        this.additionalCodeReposPaths = paths == null ? new ArrayList<>() : paths;
    }

    /**
     * Every directory the project picker browses, in order: the main code repos path, the GitHub
     * mirror if one is configured, then any extras.
     * <p>
     * mm supported N code directories (codedirscount / codedir1..N) and proj only ever browsed
     * one, which also left the GitHub mirror write-only - you could clone into it but never see
     * what was there. This is the merge of the two.
     */
    public List<String> getBrowsePaths() {
        return getCodeRepoBases().stream().map(CodeRepoBase::getPath).toList();
    }

    /**
     * Every coderepobase, with its type. Derived from the older single-path settings unless
     * {@code <codeRepoBases>} is present: codeReposPath is primary, gitHubMirrorPath is the mirror,
     * and anything in additionalCodeReposPaths is auxiliary.
     */
    public List<CodeRepoBase> getCodeRepoBases() {
        if (!codeRepoBases.isEmpty()) return codeRepoBases.stream().filter(b -> b.getPath() != null).toList();

        var out = new java.util.LinkedHashMap<String, CodeRepoBase>();
        if (codeReposPath != null && !codeReposPath.isBlank()) {
            out.put(codeReposPath.trim(), new CodeRepoBase(codeReposPath.trim(), CodeRepoBaseType.PRIMARY));
        }
        additionalCodeReposPaths.stream().filter(p -> p != null && !p.isBlank()).map(String::trim)
                .forEach(p -> out.putIfAbsent(p, new CodeRepoBase(p, CodeRepoBaseType.AUXILIARY)));
        if (gitHubMirrorPath != null && !gitHubMirrorPath.isBlank()) {
            out.putIfAbsent(gitHubMirrorPath.trim(),
                    new CodeRepoBase(gitHubMirrorPath.trim(), CodeRepoBaseType.GITHUB_MIRROR));
        }
        return new ArrayList<>(out.values());
    }

    public void setCodeRepoBases(List<CodeRepoBase> bases) {
        this.codeRepoBases = bases == null ? new ArrayList<>() : bases;
    }

    public List<CodeRepoBase> getExplicitCodeRepoBases() {
        return codeRepoBases;
    }

    /**
     * Adds or retypes a coderepobase. Materialises the derived set first, so adding one by hand
     * does not silently discard the ones that were being inferred from the older settings.
     */
    public void addCodeRepoBase(String path, CodeRepoBaseType type) {
        if (path == null || path.isBlank()) return;
        if (codeRepoBases.isEmpty()) codeRepoBases = new ArrayList<>(getCodeRepoBases());

        var target = java.nio.file.Paths.get(path.trim());
        codeRepoBases.stream().filter(b -> b.sameLocationAs(target)).findFirst()
                .ifPresentOrElse(b -> b.setType(type),
                        () -> codeRepoBases.add(new CodeRepoBase(path.trim(), type)));
    }

    public boolean removeCodeRepoBase(String path) {
        if (path == null || path.isBlank()) return false;
        if (codeRepoBases.isEmpty()) codeRepoBases = new ArrayList<>(getCodeRepoBases());
        var target = java.nio.file.Paths.get(path.trim());
        return codeRepoBases.removeIf(b -> b.sameLocationAs(target));
    }

    public CodeRepoBaseType typeOf(java.nio.file.Path path) {
        return getCodeRepoBases().stream().filter(b -> b.sameLocationAs(path))
                .map(CodeRepoBase::getType).findFirst().orElse(CodeRepoBaseType.AUXILIARY);
    }

    public List<IdeLauncher> getIdeLaunchers() {
        return ideLaunchers;
    }

    public void setIdeLaunchers(List<IdeLauncher> ideLaunchers) {
        this.ideLaunchers = ideLaunchers == null ? new ArrayList<>() : ideLaunchers;
    }

    /**
     * The launchers the picker should render columns for. When none are configured, the primary
     * ideLauncher becomes the single entry, so an existing install still gets a working column
     * rather than none at all.
     */
    public List<IdeLauncher> getEffectiveIdeLaunchers() {
        var usable = ideLaunchers.stream().filter(IdeLauncher::isUsable).toList();
        if (!usable.isEmpty()) return usable;
        return (ideLauncher == null || ideLauncher.isBlank())
                ? List.of() : List.of(new IdeLauncher("IDE", ideLauncher));
    }

    public List<TerminalLauncher> getTerminalLaunchers() {
        return terminalLaunchers == null ? new ArrayList<>() : terminalLaunchers;
    }

    public void setTerminalLaunchers(List<TerminalLauncher> terminalLaunchers) {
        this.terminalLaunchers = terminalLaunchers == null ? new ArrayList<>() : terminalLaunchers;
    }

    public String getDefaultTerminal() {
        return defaultTerminal;
    }

    public void setDefaultTerminal(String defaultTerminal) {
        this.defaultTerminal = defaultTerminal;
    }

    public String getAgentCommand() {
        return agentCommand;
    }

    public void setAgentCommand(String agentCommand) {
        this.agentCommand = agentCommand;
    }

    public List<Organization> getOrganizations() {
        return organizations;
    }

    public void setOrganizations(List<Organization> organizations) {
        this.organizations = organizations;
    }

    public String getPrpTemplateSrc() {
        return prpTemplateSrc;
    }

    public void setPrpTemplateSrc(String prpTemplateSrc) {
        this.prpTemplateSrc = prpTemplateSrc;
    }

    public String getStatusTemplate() {
        return statusTemplate;
    }

    public void setStatusTemplate(String statusTemplate) {
        this.statusTemplate = statusTemplate;
    }

    /**
     * The picker's columns, left to right, by {@code PickerColumn} id.
     * <p>
     * Empty means "the built-in order", which is not the same as "no columns" - a picker that
     * remembered an empty list as a layout would open showing nothing at all.
     */
    public List<String> getPickerColumnOrder() {
        return pickerColumnOrder == null ? new ArrayList<>() : pickerColumnOrder;
    }

    public void setPickerColumnOrder(List<String> ids) {
        this.pickerColumnOrder = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    /** Columns to start hidden. Empty means each column's own default, not "show everything". */
    public List<String> getPickerHiddenColumns() {
        return pickerHiddenColumns == null ? new ArrayList<>() : pickerHiddenColumns;
    }

    public void setPickerHiddenColumns(List<String> ids) {
        this.pickerHiddenColumns = ids == null ? new ArrayList<>() : new ArrayList<>(ids);
    }

    public int getProjectScanningDepth() {
        return projectScanningDepth;
    }

    public void setProjectScanningDepth(int projectScanningDepth) {
        this.projectScanningDepth = projectScanningDepth;
    }
}
