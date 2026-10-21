package littlejlib.proj.cct.ui;

import module java.base;

import javafx.scene.input.*;

public final class Shell {

    private Shell() {}

    public static void copy(String text) {
        if (text == null || text.isBlank()) return;
        var c = new ClipboardContent();
        c.putString(text);
        Clipboard.getSystemClipboard().setContent(c);
    }

    public static boolean reveal(String dir) {
        return run(new ProcessBuilder("explorer.exe", normalize(dir)));
    }

    /**
     * With no template configured this delegates to proj's own terminal handling rather than
     * spawning a Command Prompt of its own - there is one place that knows which terminal the user
     * wants, and a second hardcoded {@code cmd.exe /k cd /d} here was how it got out of step.
     */
    public static boolean terminal(String dir, String command) {
        var d = normalize(dir);
        if (d == null) return false;
        if (command == null || command.isBlank()) {
            return littlejlib.proj.terminal.TerminalRunner.open(Path.of(d), null);
        }
        return run(new ProcessBuilder(split(command.replace("{dir}", d))).directory(new File(d)));
    }

    private static List<String> split(String cmd) {
        var out = new ArrayList<String>();
        var m = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(cmd);
        while (m.find()) out.add(m.group(1) != null ? m.group(1) : m.group(2));
        return out;
    }

    private static String normalize(String dir) {
        if (dir == null || dir.isBlank()) return null;
        var p = Path.of(dir);
        return Files.isDirectory(p) ? p.toString() : null;
    }

    private static boolean run(ProcessBuilder pb) {
        try {
            pb.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
