package littlejlib.proj;

import littlejlib.proj.util.AppPaths;
import littlejlib.proj.util.Base52;
import littlejlib.proj.util.CRC32Util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Where a new PRP's text comes from.
 * <p>
 * IT SHIPS IN THE JAR. The template used to be fetched over HTTP from a GitHub Pages site belonging
 * to the organisation this project was under two moves ago, cached under the home directory, and
 * backed by a four-line hardcoded string when the fetch failed. Every part of that was a liability:
 * making a PRP needed the network, the URL in an existing settings file outlived the organisation
 * that served it, and the silent fallback meant a failed fetch produced a PRP missing most of its
 * own instructions - with nothing on screen to say so.
 * <p>
 * A template is a few kilobytes and changes about once a year. It belongs beside the code that uses
 * it, which is also what makes {@code proj} work on a machine that has never had network access.
 * <p>
 * A URL or a local path in settings still WINS - that is what the setting is for, and it is how a
 * team shares one template. What no longer happens is a URL winning by accident, because it was
 * written into a settings file by a version of this tool that has since moved twice.
 */
public class PRPTemplateLoader {

    /** The bundled template, on the classpath. Also the value {@code prpTemplateSrc} takes. */
    public static final String DEFAULT_TEMPLATE_SRC = "bundled";

    static final String BUNDLED_RESOURCE = "/prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md";

    /**
     * Sources that are known dead and are treated as if unset.
     * <p>
     * Both are URLs this tool itself wrote into settings files. The first belongs to the
     * organisation the project left in PRP-14; the second was written by the PRP-23 move and points
     * at a GitHub Pages site that was never published. A user who has one of these has not chosen a
     * template - they have a fossil of where this tool used to keep its own default, and honouring
     * it means a network round trip that fails and a PRP built from the stub.
     * <p>
     * Listed exactly rather than matched by pattern, so a Pages URL somebody does publish one day
     * still works.
     */
    static final java.util.Set<String> STALE = java.util.Set.of(
            "https://xyz-jphil.github.io/xyz-jphil-ai-proj_build_dev_misc_tools/prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md",
            "https://littlejlib.github.io/proj/prp-templates/PRPTemplate-v2026-01-29_2045Z-klwXbO.md");

    /** True when this setting is not a choice: unset, the bundled sentinel, or a dead URL. */
    public static boolean isDefault(String src) {
        return src == null || src.isBlank()
                || DEFAULT_TEMPLATE_SRC.equalsIgnoreCase(src.trim())
                || STALE.contains(src.trim());
    }

    /** What the setting should say, migrating a dead URL to the bundled template. */
    public static String effectiveSrc(String src) {
        return isDefault(src) ? DEFAULT_TEMPLATE_SRC : src.trim();
    }

    /**
     * Loads the PRP template from the specified source.
     *
     * @param src an http/https URL, a local file path, {@code bundled}, or null for the bundled one
     * @return The template content as a string
     * @throws IOException If a template that WAS chosen cannot be read
     */
    public static String loadTemplate(String src) throws IOException {
        if (isDefault(src)) return bundled();

        src = src.trim();
        if (src.toLowerCase().startsWith("http://") || src.toLowerCase().startsWith("https://")) {
            return loadFromUrl(src);
        }
        return loadFromLocalFile(src);
    }

    /** Never fails on a correctly built jar; a broken one is worth an exception rather than a stub. */
    public static String bundled() throws IOException {
        try (var in = PRPTemplateLoader.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) throw new IOException("the bundled PRP template is missing from this build: "
                    + BUNDLED_RESOURCE);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Loads template from a remote URL and caches it locally.
     *
     * @param urlString The HTTP/HTTPS URL to fetch from
     * @return The template content
     * @throws IOException If download or caching fails
     */
    private static String loadFromUrl(String urlString) throws IOException {
        try {
            // Extract filename from URL
            String fileName = extractFileName(urlString);
            Path cacheDir = getCacheDirectory();
            Path cachedFile = cacheDir.resolve(fileName);

            // Check if cached version exists
            if (Files.exists(cachedFile)) {
                try {
                    return Files.readString(cachedFile);
                } catch (IOException e) {
                    // If reading cached file fails, try to re-download
                    System.err.println("Warning: Failed to read cached template, re-downloading: " + e.getMessage());
                }
            }

            // Download from URL
            URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10 seconds
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Download and cache
                try (InputStream in = connection.getInputStream()) {
                    Files.createDirectories(cacheDir);
                    Files.copy(in, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // Read and return content
                return Files.readString(cachedFile);
            } else {
                throw new IOException("HTTP error fetching template: " + responseCode + " " + connection.getResponseMessage());
            }
        } catch (IOException e) {
            // The bundled template, not a four-line stub. A configured URL that cannot be reached
            // is worth saying out loud, and worth still producing a COMPLETE PRP - the old stub
            // silently made one missing most of its own usage guide.
            System.err.println("[proj] could not fetch the PRP template from " + urlString
                    + " (" + e.getMessage() + ") - using the one built into proj");
            return bundled();
        }
    }

    /**
     * Loads template from a local file path.
     *
     * @param filePath The local file path
     * @return The template content
     * @throws IOException If file cannot be read
     */
    private static String loadFromLocalFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            throw new IOException("Template file not found: " + filePath);
        }

        if (!Files.isRegularFile(path)) {
            throw new IOException("Template path is not a file: " + filePath);
        }

        return Files.readString(path);
    }

    /**
     * Extracts filename from URL.
     *
     * @param urlString The URL
     * @return The filename
     */
    private static String extractFileName(String urlString) {
        String[] parts = urlString.split("/");
        String fileName = parts[parts.length - 1];

        // Remove query parameters if any
        int queryIndex = fileName.indexOf('?');
        if (queryIndex > 0) {
            fileName = fileName.substring(0, queryIndex);
        }

        // Ensure it has .md extension
        if (!fileName.toLowerCase().endsWith(".md")) {
            fileName = fileName + ".md";
        }

        return fileName;
    }

    /**
     * Gets the cache directory path, creating it if it doesn't exist.
     *
     * @return Path to cache directory
     * @throws IOException If directory cannot be created
     */
    private static Path getCacheDirectory() throws IOException {
        var cacheDir = AppPaths.prpTemplateCache();

        if (!Files.exists(cacheDir)) {
            Files.createDirectories(cacheDir);
        }

        return cacheDir;
    }

    /**
     * Clears the template cache directory.
     *
     * @throws IOException If cache cannot be cleared
     */
    public static void clearCache() throws IOException {
        Path cacheDir = getCacheDirectory();

        if (Files.exists(cacheDir)) {
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to delete cached file: " + file);
                    }
                });
        }
    }
}
