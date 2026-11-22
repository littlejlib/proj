package littlejlib.proj.migrate;

import module java.base;

import com.google.gson.Gson;

/** A prp folder's {@code renumber-map.jsonl}: old file and number to new, as written by {@code proj prp renumber}. */
public final class FolderMap {

    public static final String FILE = "renumber-map.jsonl";
    static final Set<String> SKIP = Set.of(".git", "node_modules", "target", ".claude");
    static final Gson GSON = new Gson();

    final Path dir;
    final List<MapEntry> entries;
    final int width;

    FolderMap(Path dir, List<MapEntry> entries) {
        this.dir = dir.toAbsolutePath().normalize();
        this.entries = entries;
        this.width = ShortNumber.width(entries.stream().filter(MapEntry::main).map(MapEntry::newNumber).toList());
    }

    public Path dir() {
        return dir;
    }

    public Path project() {
        return dir.getParent();
    }

    public int width() {
        return width;
    }

    public List<MapEntry> mains() {
        return entries.stream().filter(MapEntry::main).toList();
    }

    public static FolderMap parse(Path dir, List<String> lines) {
        var out = new ArrayList<MapEntry>();
        for (var line : lines) {
            if (line.isBlank()) continue;
            var e = GSON.fromJson(line, MapEntry.class);
            if (e == null || e.oldFile() == null || e.newFile() == null || e.newNumber() == null)
                throw new IllegalArgumentException("bad line in " + dir.resolve(FILE) + ": " + line);
            out.add(e);
        }
        return new FolderMap(dir, out);
    }

    public static FolderMap read(Path dir) throws IOException {
        return parse(dir, Files.readAllLines(dir.resolve(FILE), StandardCharsets.UTF_8));
    }

    /** Every prp folder at most {@code depth} levels under root that holds a map. */
    public static List<Path> find(Path root, int depth) throws IOException {
        var found = new ArrayList<Path>();
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), depth + 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                var name = d.getFileName() == null ? "" : d.getFileName().toString();
                return SKIP.contains(name) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                if (FILE.equals(f.getFileName().toString())) found.add(f.getParent());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path f, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        found.sort(Comparator.naturalOrder());
        return found;
    }

    boolean owns(String file) {
        try {
            var parent = Path.of(file).toAbsolutePath().normalize().getParent();
            return dir.equals(parent);
        } catch (InvalidPathException e) {
            return false;
        }
    }

    static String nameOf(String file) {
        var p = Path.of(file).getFileName();
        return p == null ? file : p.toString();
    }

    MapEntry mainByFile(String name) {
        return entries.stream().filter(e -> e.main() && e.oldFile().equals(name)).findFirst().orElse(null);
    }

    MapEntry mainByNumber(String oldNumber) {
        return entries.stream().filter(e -> e.main() && e.oldNumber() != null && e.oldNumber().equalsIgnoreCase(oldNumber.strip()))
                .findFirst().orElse(null);
    }

    boolean isNewMain(String name) {
        return entries.stream().anyMatch(e -> e.main() && e.newFile().equals(name));
    }

    MapEntry byOldFile(String name) {
        return entries.stream().filter(e -> e.oldFile().equals(name)).findFirst().orElse(null);
    }

    String shortOf(MapEntry e) {
        return ShortNumber.of(e.newNumber(), width);
    }

    /** True when this tab name carries the old number of one of the folder's PRPs. */
    boolean looksOld(String name) {
        return mains().stream().anyMatch(e -> Rewriter.markerAt(name, e.oldNumber()) >= 0);
    }
}
