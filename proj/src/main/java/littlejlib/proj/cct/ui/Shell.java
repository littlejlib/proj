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

    public static boolean terminal(String dir, String command) {
        var d = normalize(dir);
        if (d == null) return false;
        var cmd = command == null || command.isBlank() ? defaultTerminal() : command;
        return run(new ProcessBuilder(split(cmd.replace("{dir}", d))).directory(new File(d)));
    }

    private static String defaultTerminal() {
        return "cmd.exe /c start \"\" cmd.exe /k cd /d \"{dir}\"";
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
