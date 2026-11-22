package littlejlib.proj.prp;

import com.google.gson.JsonParser;
import littlejlib.proj.ai.Ai;
import littlejlib.proj.ai.AiConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Short names for a new PRP (PRP 001R): asked of the AI providers in {@code ai.toml} with the naming prompt, checked,
 * and made unique in the folder; the mechanical rule ({@link PrpShortName}) when no provider answers. The user
 * renames them in the file afterwards.
 */
public final class PrpNames {

    private PrpNames() { }

    public record Chosen(String shortName, String threeLetters, String by) { }

    static final Pattern SHORT = Pattern.compile("[a-z0-9][a-z0-9-]{0,27}"), THREE = Pattern.compile("[a-z]{3}");

    public static Chosen choose(Path prpDir, String projectName, String prpName, Path except) {
        var takenShort = new HashSet<String>();
        var takenThree = new HashSet<String>();
        try (var files = Files.list(prpDir)) {
            for (var f : (Iterable<Path>) files::iterator) {
                if (f.equals(except) || !PrpFiles.mainMatcher(f.getFileName().toString()).matches()) continue;
                var h = PrpHeader.of(f);
                if (h.shortName != null) takenShort.add(h.shortName);
                if (h.threeLetters != null) takenThree.add(h.threeLetters);
            }
        } catch (IOException ignored) {
            // an unreadable folder only weakens the uniqueness check
        }
        try {
            var cfg = AiConfig.load();
            if (!cfg.provider.isEmpty()) {
                var user = "Project: " + projectName + "\nPRP name: " + prpName
                        + "\nShort names already used in this folder: " + String.join(", ", takenShort)
                        + "\n3-letter names already used in this folder: " + String.join(", ", takenThree);
                var answer = Ai.ask(cfg, prompt(cfg), user);
                var json = answer.text().substring(answer.text().indexOf('{'), answer.text().lastIndexOf('}') + 1);
                var o = JsonParser.parseString(json).getAsJsonObject();
                var s = PrpShortName.dropProjectPrefix(o.get("short").getAsString().toLowerCase().replaceAll("[^a-z0-9-]", ""), projectName, forms(cfg));
                var t = o.get("three").getAsString().toLowerCase();
                if (SHORT.matcher(s).matches()) {
                    s = unique(s, takenShort);
                    if (!THREE.matcher(t).matches() || takenThree.contains(t)) t = PrpShortName.derive(s, projectName, takenThree).threeLetters();
                    return new Chosen(s, t, answer.provider());
                }
            }
        } catch (Exception e) {
            System.err.println("[prp names] no AI names (" + e.getMessage() + "); using the rule");
        }
        var n = PrpShortName.derive(prpName, projectName, takenThree);
        return new Chosen(unique(n.shortName(), takenShort), n.threeLetters(), "rule");
    }

    /** The short forms of the user's file (first column), which also stay at the front of a name. */
    static Set<String> forms(AiConfig cfg) {
        var out = new HashSet<String>();
        if (cfg.shortForms == null || cfg.shortForms.isBlank()) return out;
        try {
            for (var line : Files.readAllLines(Path.of(cfg.shortForms), StandardCharsets.UTF_8)) {
                var f = line.split("\t");
                if (f.length >= 1 && !f[0].isBlank() && !f[0].equalsIgnoreCase("short form")) out.add(f[0].strip().toLowerCase());
            }
        } catch (IOException ignored) {
            // no file, no extra forms
        }
        return out;
    }

    static String unique(String s, Set<String> taken) {
        if (!taken.contains(s)) return s;
        for (int i = 2; ; i++) if (!taken.contains(s + "-" + i)) return s + "-" + i;
    }

    /** The prompt: the user's own file when ai.toml names one, else the one shipped in proj; short forms appended. */
    static String prompt(AiConfig cfg) throws IOException {
        String text;
        if (cfg.shortNamesPrompt != null && !cfg.shortNamesPrompt.isBlank()) text = Files.readString(Path.of(cfg.shortNamesPrompt), StandardCharsets.UTF_8);
        else try (var in = PrpNames.class.getResourceAsStream("/prp-names/short-names-prompt.md")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var forms = new StringBuilder();
        if (cfg.shortForms != null && !cfg.shortForms.isBlank()) {
            forms.append("Short forms (form = meaning, score 3 universal / 2 known here / 1 careful): ");
            for (var line : Files.readAllLines(Path.of(cfg.shortForms), StandardCharsets.UTF_8)) {
                var f = line.split("\t");
                if (f.length >= 2 && !f[0].equalsIgnoreCase("short form")) forms.append(f[0]).append(" = ").append(f[1]).append(f.length > 2 ? " (" + f[2] + ")" : "").append("; ");
            }
            forms.append('\n');
        }
        return text.replace("{{SHORT_FORMS}}", forms.toString());
    }
}
