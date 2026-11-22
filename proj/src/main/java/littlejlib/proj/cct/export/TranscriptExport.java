package littlejlib.proj.cct.export;

import module java.base;

import littlejlib.proj.cct.model.*;
import littlejlib.proj.cct.store.StorePaths;

/**
 * One conversation onto disk, in whichever of the four shapes was asked for.
 * <p>
 * {@code JSONL} is not rendered at all - it is a byte copy of the file claude-code wrote, which is
 * the only export that keeps the tool results, the images and the tree structure the index throws
 * away. It is also the only one that can be large, so it says so rather than being quietly slow.
 */
public final class TranscriptExport {

    private TranscriptExport() {}

    public static String render(SessionDoc s, List<TurnDoc> turns, ExportFormat format) {
        return switch (format) {
            case MARKDOWN -> MarkdownExport.render(s, turns);
            case TEXT -> TextExport.render(s, turns);
            case XHTML -> XHtmlExport.render(s, turns);
            case JSONL -> throw new IllegalArgumentException("jsonl is copied, not rendered");
        };
    }

    public static Path write(SessionDoc s, List<TurnDoc> turns, ExportFormat format, Path target)
            throws IOException {
        var dir = target.getParent();
        if (dir != null) Files.createDirectories(dir);
        if (format == ExportFormat.JSONL) return copyRaw(s, target);
        Files.writeString(target, render(s, turns, format), StandardCharsets.UTF_8);
        return target;
    }

    public static Path writeToExportDir(SessionDoc s, List<TurnDoc> turns, ExportFormat format)
            throws IOException {
        StorePaths.ensureAppHome();
        return write(s, turns, format, ExportNames.resolve(StorePaths.exportDir(), s, format));
    }

    static Path copyRaw(SessionDoc s, Path target) throws IOException {
        if (s.filePath() == null || s.filePath().isBlank())
            throw new IOException("no transcript file recorded for this session"
                + (Boolean.TRUE.equals(s.transcriptMissing()) ? " - claude-code pruned it" : ""));
        var src = Path.of(s.filePath());
        if (!Files.isRegularFile(src))
            throw new IOException("the transcript file is gone: " + src);
        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
}
