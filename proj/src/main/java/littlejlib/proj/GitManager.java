package littlejlib.proj;

import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GitManager {

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile("https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    public static void cloneRepository(Terminal terminal, LineReader lineReader, Settings settings) {
        terminal.writer().println("\n--- Git Clone Repository ---");
        terminal.writer().flush();

        String gitUrl = lineReader.readLine("Enter GitHub repository URL: ").trim();
        if (gitUrl.isEmpty()) {
            terminal.writer().println("URL cannot be empty.");
            terminal.writer().flush();
            return;
        }

        Matcher matcher = GITHUB_URL_PATTERN.matcher(gitUrl);
        if (!matcher.matches()) {
            terminal.writer().println("Invalid GitHub URL format.");
            terminal.writer().println("Expected format: https://github.com/<organization>/<repository>");
            terminal.writer().flush();
            return;
        }

        String organization = matcher.group(1);
        String repository = matcher.group(2);

        boolean internal = settings.isConfiguredOrganization(organization);
        String basePath = settings.getCloneBasePathForOrg(organization);

        terminal.writer().println("\nDetected:");
        terminal.writer().println("  Organization: " + organization
                + (internal ? " (configured/internal)" : " (external)"));
        terminal.writer().println("  Repository: " + repository);
        terminal.writer().println("  Destination base: " + basePath
                + (internal ? "  [code repos]" : "  [github mirror]"));
        terminal.writer().flush();

        Path orgPath = Paths.get(basePath, organization);
        Path repoPath = orgPath.resolve(repository);

        if (Files.exists(repoPath)) {
            terminal.writer().println("\nRepository already exists at: " + repoPath);
            terminal.writer().flush();
            return;
        }

        try {
            if (!Files.exists(orgPath)) {
                terminal.writer().println("Creating organization directory: " + orgPath);
                Files.createDirectories(orgPath);
                terminal.writer().flush();
            }

            terminal.writer().println("\nCloning repository...");
            terminal.writer().println("Target directory: " + orgPath);
            terminal.writer().flush();

            ProcessBuilder pb = new ProcessBuilder("git", "clone", gitUrl);
            pb.directory(orgPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                terminal.writer().println(line);
                terminal.writer().flush();
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                terminal.writer().println("\nRepository cloned successfully to: " + repoPath);
            } else {
                terminal.writer().println("\nGit clone failed with exit code: " + exitCode);
            }
            terminal.writer().flush();

        } catch (IOException e) {
            terminal.writer().println("Error during clone operation: " + e.getMessage());
            terminal.writer().flush();
        } catch (InterruptedException e) {
            terminal.writer().println("Clone operation interrupted: " + e.getMessage());
            terminal.writer().flush();
            Thread.currentThread().interrupt();
        }
    }
}
