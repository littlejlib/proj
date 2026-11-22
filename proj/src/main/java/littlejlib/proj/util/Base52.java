package littlejlib.proj.util;

/**
 * Base52 encoding/decoding utility.
 * Uses characters: a-z (26) + A-Z (26) = 52 total characters
 * Alphabet: 'a'=0, 'b'=1, ... 'z'=25, 'A'=26, 'B'=27, ... 'Z'=51
 * Suitable for encoding 32-bit CRC32 checksums in 1-6 characters.
 * This implementation left-pads with 'a' (zero) to ensure fixed length of 6 characters.
 */
public class Base52 {
    // Base52 alphabet: a-z followed by A-Z (where 'a' = 0, 'z' = 25, 'A' = 26, 'Z' = 51)
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 52;
    private static final int CRC32_LENGTH = 6; // Fixed length (left-padded with 'a' for values < 6 chars)

    /**
     * Encodes a 32-bit unsigned integer (CRC32) to base52 string.
     *
     * @param value The 32-bit value to encode (treat as unsigned)
     * @return Base52 encoded string of fixed length 6 characters (left-padded with 'a' which represents zero)
     */
    public static String encode(long value) {
        // Ensure it's treated as unsigned 32-bit
        value = value & 0xFFFFFFFFL;

        StringBuilder result = new StringBuilder();

        // Convert to base52
        do {
            int remainder = (int) (value % BASE);
            result.insert(0, ALPHABET.charAt(remainder));
            value = value / BASE;
        } while (value > 0);

        // Left-pad with 'a' (represents zero) to ensure fixed length of 6 characters
        while (result.length() < CRC32_LENGTH) {
            result.insert(0, 'a');
        }

        return result.toString();
    }

    /**
     * Decodes a base52 string back to a 32-bit unsigned integer.
     *
     * @param encoded The base52 encoded string
     * @return The decoded 32-bit value
     * @throws IllegalArgumentException if the string is invalid
     */
    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string cannot be null or empty");
        }

        long result = 0;
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            int digit = ALPHABET.indexOf(c);
            if (digit == -1) {
                throw new IllegalArgumentException("Invalid character in base52 string: " + c);
            }
            result = result * BASE + digit;
        }

        return result;
    }

    /**
     * Extracts CRC32 signature from filename.
     * Pattern: filename-base52crc.extension
     *
     * @param filename The filename to extract from
     * @return The base52 CRC string, or null if not found
     */
    public static String extractCrcFromFilename(String filename) {
        // Pattern: 1-6 base52 characters after last dash before extension
        // Example: PRPTemplate-v2026-01-29_2045Z-klwXbO.md
        // Accepts 1-6 characters (not all implementations may zero-pad)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-([a-zA-Z]{1,6})\\.[^.]+$");
        java.util.regex.Matcher matcher = pattern.matcher(filename);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Checks if a filename contains a CRC32 signature.
     *
     * @param filename The filename to check
     * @return true if the filename contains a valid CRC32 signature pattern
     */
    public static boolean hasCrcSignature(String filename) {
        return extractCrcFromFilename(filename) != null;
    }

    /**
     * Validates that a base52 string matches the expected format.
     *
     * @param encoded The string to validate
     * @return true if valid base52 format
     */
    public static boolean isValidBase52(String encoded) {
        if (encoded == null || encoded.length() < 1 || encoded.length() > CRC32_LENGTH) {
            return false;
        }

        return encoded.matches("[a-zA-Z]{1,6}");
    }
}
