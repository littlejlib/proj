package littlejlib.proj;

import java.nio.file.Path;

public class TerminalHelper {

    /**
     * Write a postrun script to change the parent terminal's directory.
     * The parent wrapper (proj.bat on Windows, the proj shell function on
     * macOS/Linux) executes this script after the Java program exits. On
     * Windows the script is batch; on macOS/Linux it is POSIX shell that the
     * wrapper sources so the directory change applies to the current shell.
     *
     * @param targetDirectory The directory to change to
     * @return true if the postrun script was successfully written, false otherwise
     */
    public static boolean writePostrunBatch(Path targetDirectory) {
        String postrunPath = System.getenv("PROJ_POSTRUN_BATCH");

        if (postrunPath == null || postrunPath.isEmpty()) {
            // Not running from the proj wrapper, this feature is not available
            return false;
        }

        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        String dir = targetDirectory.toAbsolutePath().toString();

        try (java.io.PrintWriter writer = new java.io.PrintWriter(postrunPath, "UTF-8")) {
            if (windows) {
                writer.println("@echo off");
                writer.println("cd /d \"" + dir + "\"");
                writer.println("echo.");
                writer.println("echo Successfully changed directory to:");
                writer.println("echo   %CD%");
                writer.println("echo.");
                writer.println("del \"" + postrunPath + "\" 2>nul");
            } else {
                writer.println("cd \"" + dir + "\"");
                writer.println("echo \"\"");
                writer.println("echo \"Successfully changed directory to:\"");
                writer.println("echo \"  $PWD\"");
                writer.println("echo \"\"");
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error writing postrun script: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if the postrun batch feature is available.
     * This is true when the program is launched via proj.bat which sets the
     * PROJ_POSTRUN_BATCH environment variable.
     *
     * @return true if postrun batch feature is available
     */
    public static boolean isPostrunBatchAvailable() {
        String postrunBatchPath = System.getenv("PROJ_POSTRUN_BATCH");
        return postrunBatchPath != null && !postrunBatchPath.isEmpty();
    }
}
