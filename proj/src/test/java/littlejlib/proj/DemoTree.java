package littlejlib.proj;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/// An invented code folder for the screenshots: neutral names, example.org, a git repository each.
final class DemoTree {
    private DemoTree() {}

    private record Prp(String number, String name, String shortName, String letters) {}

    static void build(Path code) throws IOException, InterruptedException {
        project(code.resolve("acme/billing"), "billing", "billing",
                List.of(new Prp("0001", "invoice export to csv", "csv", "iec"),
                        new Prp("0002", "tax rounding rules", "tax", "trr"),
                        new Prp("0003", "retry failed card payments", "retry", "rfc")));
        project(code.resolve("acme/exporter"), "exporter", "exporter",
                List.of(new Prp("0001", "stream rows instead of buffering", "stream", "sri"),
                        new Prp("0002", "excel output", "excel", "exo")));
        project(code.resolve("acme/ledger"), "ledger", "ledger",
                List.of(new Prp("0001", "double entry checks", "checks", "dec")));
        project(code.resolve("acme/docs-site"), "docs-site", "docs", List.of());
        project(code.resolve("acme/mailer"), "mailer", "mailer", List.of());
        project(code.resolve("labs/parser-kit"), "parser-kit", "parser", List.of());
        project(code.resolve("labs/scheduler"), "scheduler", "sched",
                List.of(new Prp("0001", "cron expressions with time zones", "cron", "cet")));
    }

    private static void project(Path dir, String artifact, String key, List<Prp> prps) throws IOException, InterruptedException {
        Files.createDirectories(dir.resolve("src/main/java"));
        Files.writeString(dir.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example.%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.%d.0</version>
                </project>
                """.formatted(dir.getParent().getFileName(), artifact, Math.abs(artifact.hashCode()) % 9));
        if (!prps.isEmpty()) {
            var prp = Files.createDirectories(dir.resolve("prp"));
            Files.writeString(prp.resolve("proj.json"), "{\"name\":\"" + key + "\"}\n");
            for (var p : prps) {
                Files.writeString(prp.resolve(p.number + "00-" + p.name.replace(' ', '_') + ".md"), """
                        ---
                        prp_number: %d
                        prp_number_b34str: "%s"
                        prp_name: %s
                        prp_3_letter_name: %s
                        prp_short_name: %s
                        prp_uuid: %s
                        ---

                        # Requirement

                        %s.
                        """.formatted(Integer.parseInt(p.number), p.number, p.name, p.letters, p.shortName,
                        UUID.randomUUID().toString().replace("-", "").substring(0, 25), p.name));
            }
        }
        git(dir, "init", "-q");
        git(dir, "add", "-A");
        git(dir, "-c", "user.name=Demo", "-c", "user.email=demo@example.org", "commit", "-q", "-m", "first commit");
    }

    private static void git(Path dir, String... args) throws IOException, InterruptedException {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        var p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        p.waitFor();
    }
}
