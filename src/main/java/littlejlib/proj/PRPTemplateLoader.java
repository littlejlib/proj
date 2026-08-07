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
 * Handles loading PRP templates from various sources (HTTP/HTTPS URLs or local file paths)
 * and caches remote templates locally.
 */
public class PRPTemplateLoader {
    /**
     * Base URL for GitHub Pages hosted templates
     */
    private static final String GITHUB_PAGES_BASE_URL = "https://littlejlib.github.io/proj/prp-templates/";

    /**
     * Default PRP template filename
     * Format: PRPTemplate-v{YYYY-MM-DD}_{HHMM}Z-{CRC32_BASE52}.md
     * Filename includes UTC timestamp (Z suffix) and CRC32 checksum in base52 format
     * CRC32 calculated on normalized line endings (Unix \n style) for cross-platform consistency
     */
    private static final String DEFAULT_TEMPLATE_FILENAME = "PRPTemplate-v2026-01-29_2045Z-klwXbO.md";

    /**
     * Default PRP template URL (GitHub Pages)
     */
    public static final String DEFAULT_TEMPLATE_SRC = GITHUB_PAGES_BASE_URL + DEFAULT_TEMPLATE_FILENAME;

    /**
     * Fallback template content if remote fetch fails
     */
    private static final String FALLBACK_TEMPLATE = "PRP Number: %index%\n" +
            "PRP Name: %name%\n" +
            "Usage Guide: \n" +
            "\t- This is a Project Requirement Prompt (PRP). This file contains AI prompts that are intended to define certain requirement(s) for this project. \n" +
            "\t- Claude Code (or any other coding AI agents) will be working on implementing this requirement in this project when told by the user. For AI coding agents this file is READ-ONLY and MUST NOT be modified by AI coding agents. \n" +
            "\t- Once this PRP is completed (or temporarily stalled), it is renamed to `%index%-prp-%name%.closed.md`. \n" +
            "\t- The file `%index%-prp.status.md` carries the status update for this prp, which is to be written/updated by the AI coding agents working on this prp.\n";

    /**
     * Loads the PRP template from the specified source.
     *
     * @param src Source URL (http/https) or local file path. If null or empty, uses default.
     * @return The template content as a string
     * @throws IOException If template cannot be loaded
     */
    public static String loadTemplate(String src) throws IOException {
        // Use default if src is null or empty
        if (src == null || src.trim().isEmpty()) {
            src = DEFAULT_TEMPLATE_SRC;
        }

        src = src.trim();

        // Check if it's a URL (http or https)
        if (src.toLowerCase().startsWith("http://") || src.toLowerCase().startsWith("https://")) {
            return loadFromUrl(src);
        } else {
            // Treat as local file path
            return loadFromLocalFile(src);
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
            // If all else fails, use fallback template
            System.err.println("Error loading template from URL: " + e.getMessage());
            System.err.println("Using fallback template.");
            return FALLBACK_TEMPLATE;
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
