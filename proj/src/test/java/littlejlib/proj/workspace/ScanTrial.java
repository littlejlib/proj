package littlejlib.proj.workspace;

import java.nio.file.Path;

public class ScanTrial {
    public static void main(String[] args) {
        var base = Path.of(args.length > 0 ? args[0] : "D:/shared/docs");
        var depth = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        var t0 = System.currentTimeMillis();
        var scanner = new WorkspaceScanner(depth).group("docs");
        var rows = scanner.scan(base);
        var ms = System.currentTimeMillis() - t0;

        System.out.println("scanned " + scanner.foldersScanned() + " folders, kept "
                + rows.size() + " rows in " + ms + " ms (depth " + depth + ")");
        rows.stream()
                .sorted((a, b) -> Long.compare(b.activity(), a.activity()))
                .limit(12)
                .forEach(r -> System.out.println("  " + (r.hasPrp() ? r.prpCount() + " prp  " : "  node  ")
                        + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date(r.activity()))
                        + "  d" + r.depth() + "  " + r.relativePath()
                        + (r.parentPath().isBlank() ? "" : "   [under " + Path.of(r.parentPath()).getFileName() + "]")));
        System.out.println("  nodes without prp: " + rows.stream().filter(r -> !r.hasPrp()).count());
    }
}
