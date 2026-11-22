import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.zip.CRC32;

/**
 * Standalone utility to version PRP template files with timestamp and CRC32 checksum.
 * Usage: java PRPTemplateVersioner.java <input-file>
 * Example: PRPTemplate-v2026-01-29_2045Z-klwXbO.md
 * Note: Timestamps are always in UTC (Z suffix)
 * Format: <basename>-v<timestamp>Z-<crc32>.<extension>
 */
public class PRPTemplateVersioner {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 52;
    private static final int CRC_LENGTH = 6;

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java PRPTemplateVersioner.java <input-file>");
            System.out.println();
            System.out.println("Example:");
            System.out.println("  Input:  prptemplate_evolving_draft.md");
            System.out.println("  Output: PRPTemplate-v2026-01-29_2045Z-klwXbO.md");
            System.exit(1);
        }

        try {
            Path inputPath = Paths.get(args[0]);
            if (!Files.exists(inputPath)) {
                System.err.println("Error: File not found: " + args[0]);
                System.exit(1);
            }

            String content = Files.readString(inputPath);
            String normalized = content.replace("\r\n", "\n").replace("\r", "\n");

            CRC32 crc = new CRC32();
            crc.update(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long crcValue = crc.getValue();
            String crcBase52 = encodeBase52(crcValue);

            FileTime lastModified = Files.getLastModifiedTime(inputPath);
            LocalDateTime dateTime = LocalDateTime.ofInstant(lastModified.toInstant(), ZoneId.of("UTC"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("'v'yyyy-MM-dd_HHmm");
            String timestamp = dateTime.format(formatter) + "Z";

            String fileName = inputPath.getFileName().toString();
            String baseName = getBaseName(fileName);
            String extension = getExtension(fileName);
            String newFileName = baseName + "-" + timestamp + "-" + crcBase52 + "." + extension;
            Path outputPath = inputPath.getParent() != null ? inputPath.getParent().resolve(newFileName) : Paths.get(newFileName);

            if (Files.exists(outputPath)) {
                System.out.print("Warning: Output file exists. Overwrite? (y/n): ");
                String response = System.console() != null ? System.console().readLine() : new java.util.Scanner(System.in).nextLine();
                if (!response.trim().equalsIgnoreCase("y")) {
                    System.out.println("Cancelled.");
                    System.exit(0);
                }
            }

            Files.copy(inputPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("=".repeat(70));
            System.out.println("PRP Template Versioned Successfully");
            System.out.println("=".repeat(70));
            System.out.println("Input file:     " + fileName);
            System.out.println("Output file:    " + newFileName);
            System.out.println("Timestamp:      " + timestamp);
            System.out.println("CRC32 (dec):    " + crcValue);
            System.out.println("CRC32 (base52): " + crcBase52);
            System.out.println("File size:      " + normalized.length() + " bytes (normalized)");
            System.out.println("=".repeat(70));
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String encodeBase52(long value) {
        value = value & 0xFFFFFFFFL;
        StringBuilder result = new StringBuilder();
        do {
            result.insert(0, ALPHABET.charAt((int)(value % BASE)));
            value /= BASE;
        } while (value > 0);
        while (result.length() < CRC_LENGTH) result.insert(0, 'a');
        return result.toString();
    }

    private static String getBaseName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private static String getExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0 && lastDot < fileName.length() - 1) ? fileName.substring(lastDot + 1) : "md";
    }

    private static String normalizeLineEndings(String content) {
        return content == null ? null : content.replace("\r\n", "\n").replace("\r", "\n");
    }
}
