package littlejlib.proj;


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

    public static void cloneRepository(Settings settings) {
        System.out.println("\n--- Git Clone Repository ---");
        System.out.flush();

        String gitUrl = Prompt.ask("Enter GitHub repository URL: ").trim();
        if (gitUrl.isEmpty()) {
            System.out.println("URL cannot be empty.");
            System.out.flush();
            return;
        }

        Matcher matcher = GITHUB_URL_PATTERN.matcher(gitUrl);
        if (!matcher.matches()) {
            System.out.println("Invalid GitHub URL format.");
            System.out.println("Expected format: https://github.com/<organization>/<repository>");
            System.out.flush();
            return;
        }

        String organization = matcher.group(1);
        String repository = matcher.group(2);

        boolean internal = settings.isConfiguredOrganization(organization);
        String basePath = settings.getCloneBasePathForOrg(organization);

        System.out.println("\nDetected:");
        System.out.println("  Organization: " + organization
                + (internal ? " (configured/internal)" : " (external)"));
        System.out.println("  Repository: " + repository);
        System.out.println("  Destination base: " + basePath
                + (internal ? "  [code repos]" : "  [github mirror]"));
        System.out.flush();

        Path orgPath = Paths.get(basePath, organization);
        Path repoPath = orgPath.resolve(repository);

        if (Files.exists(repoPath)) {
            System.out.println("\nRepository already exists at: " + repoPath);
            System.out.flush();
            return;
        }

        try {
            if (!Files.exists(orgPath)) {
                System.out.println("Creating organization directory: " + orgPath);
                Files.createDirectories(orgPath);
                System.out.flush();
            }

            System.out.println("\nCloning repository...");
            System.out.println("Target directory: " + orgPath);
            System.out.flush();

            ProcessBuilder pb = GitExe.command("clone", gitUrl);
            pb.directory(orgPath.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                System.out.flush();
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("\nRepository cloned successfully to: " + repoPath);
            } else {
                System.out.println("\nGit clone failed with exit code: " + exitCode);
            }
            System.out.flush();

        } catch (IOException e) {
            System.out.println("Error during clone operation: " + e.getMessage());
            System.out.flush();
        } catch (InterruptedException e) {
            System.out.println("Clone operation interrupted: " + e.getMessage());
            System.out.flush();
            Thread.currentThread().interrupt();
        }
    }
}
