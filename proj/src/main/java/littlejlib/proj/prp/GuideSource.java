package littlejlib.proj.prp;

import java.util.regex.Pattern;

/**
 * One entry of a guide list: {@code extend:<source>} (printed after what came before) or
 * {@code replace:<source>} (instead of everything before it). No prefix means {@code replace}, which is
 * what a single string meant before lists existed.
 * <p>
 * A source is {@code bundled}, an http(s) url, a local file, or a file fetched with git (for private
 * repositories): {@code git@host:org/repo.git#ref:path}, {@code ssh://...#ref:path},
 * {@code file://...#ref:path}, or {@code git+https://github.com/org/repo/blob/ref/path} (the https page
 * address read over SSH; a ref with a slash in it needs the {@code #ref:path} form).
 */
public record GuideSource(boolean replace, String spec) {

    static final Pattern MODE = Pattern.compile("(?i)(extend|replace):(.*)", Pattern.DOTALL);

    static final Pattern GIT_REMOTE = Pattern.compile("(?i)(git@|ssh://|git://|file://).*#[^#]*");

    static final Pattern GIT_BLOB = Pattern.compile("(?i)git\\+https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/(?:blob|raw)/([^/]+)/(.+)");

    public static GuideSource parse(String entry) {
        var e = entry == null ? "" : entry.strip();
        var m = MODE.matcher(e);
        return m.matches() ? new GuideSource("replace".equalsIgnoreCase(m.group(1)), m.group(2).strip()) : new GuideSource(true, e);
    }

    public String entry() {
        return (replace ? "replace:" : "extend:") + spec;
    }

    public boolean isBundled() {
        return PrpGuide.BUNDLED.equalsIgnoreCase(spec);
    }

    public boolean isHttp() {
        return spec.matches("(?i)https?://.*");
    }

    public boolean isGit() {
        return spec.regionMatches(true, 0, "git+", 0, 4) || GIT_REMOTE.matcher(spec).matches();
    }

    /** The git file this entry names, or null when it is not a git entry or cannot be read as one. */
    public GitFile gitFile() {
        if (!isGit()) return null;
        var b = GIT_BLOB.matcher(spec);
        if (b.matches()) return GitFile.of("git@github.com:" + b.group(1) + "/" + b.group(2) + ".git", b.group(3), b.group(4));
        var cut = spec.lastIndexOf('#');
        var at = cut < 0 ? -1 : spec.indexOf(':', cut);
        return at < 0 ? null : GitFile.of(spec.substring(0, cut), spec.substring(cut + 1, at), spec.substring(at + 1));
    }

    /** The entry as shown to people: any user:password in a url is dropped. */
    public String shown() {
        return spec.replaceAll("(?i)(://)[^/@\\s]*@", "$1");
    }
}
