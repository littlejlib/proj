package littlejlib.proj.workspace;

import java.nio.file.Files;
import java.nio.file.Path;

/** Confirms the dated-name rule against the REAL folder names, not against invented ones. */
public class DatedCheck {
    public static void main(String[] args) throws Exception {
        var base = Path.of("D:/shared/docs");
        try (var s = Files.list(base)) {
            var names = s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
            var dated = names.stream().filter(FolderNames::dated).count();
            System.out.println(names.size() + " top-level folders, " + dated + " dated");
            System.out.println("-- NOT matched (would show as plain folders) --");
            names.stream().filter(n -> !FolderNames.dated(n)).limit(20)
                    .forEach(n -> System.out.println("   " + n));
        }
    }
}
