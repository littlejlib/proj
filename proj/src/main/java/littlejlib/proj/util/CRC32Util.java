package littlejlib.proj.util;

import java.util.zip.CRC32;

/**
 * Utility class for CRC32 checksum calculations.
 * Always normalizes line endings to Unix style (\n) before calculating checksum
 * to ensure cross-platform consistency.
 */
public class CRC32Util {

    /**
     * Normalizes line endings to Unix style (\n only).
     * Replaces \r\n with \n and removes standalone \r characters.
     *
     * @param content The content to normalize
     * @return Content with normalized line endings
     */
    public static String normalizeLineEndings(String content) {
        if (content == null) {
            return null;
        }
        // Replace Windows line endings (\r\n) with Unix (\n)
        // Then remove any remaining \r characters
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }

    /**
     * Calculates CRC32 checksum of a string after normalizing line endings.
     * This ensures consistent checksums across Windows, Linux, and Mac platforms.
     *
     * @param content The string content to checksum
     * @return The CRC32 value as a long (unsigned 32-bit)
     */
    public static long calculateCRC32(String content) {
        if (content == null) {
            return 0;
        }

        // Normalize line endings before calculating CRC32
        String normalized = normalizeLineEndings(content);

        CRC32 crc = new CRC32();
        crc.update(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return crc.getValue();
    }

    /**
     * Calculates CRC32 checksum and encodes it as base52.
     *
     * @param content The string content to checksum
     * @return Base52 encoded CRC32 string
     */
    public static String calculateCRC32AsBase52(String content) {
        long crc = calculateCRC32(content);
        return Base52.encode(crc);
    }

    /**
     * Verifies that content matches the expected CRC32 signature.
     *
     * @param content The content to verify
     * @param expectedCrcBase52 The expected CRC32 in base52 format
     * @return true if the content matches the expected CRC
     */
    public static boolean verifyCRC32(String content, String expectedCrcBase52) {
        if (content == null || expectedCrcBase52 == null) {
            return false;
        }

        String actualCrc = calculateCRC32AsBase52(content);
        return actualCrc.equals(expectedCrcBase52);
    }
}
