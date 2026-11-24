package littlejlib.proj.cli;

import littlejlib.proj.store.BinCodec;
import littlejlib.proj.store.Tables;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** `proj store`: where the index files are, and any of them as JSON. */
@Command(name = "store", description = "The index files: where they are, and any of them as JSON",
        subcommands = {StoreCmd.Dump.class, StoreCmd.Where.class})
public final class StoreCmd {

    @Command(name = "where", description = "Print the cache folder (rebuilt by a scan) and the data folder (yours)")
    public static final class Where implements Runnable {
        @Override public void run() {
            littlejlib.proj.StdoutChannel.data().println("cache " + Tables.cache());
            littlejlib.proj.StdoutChannel.data().println("data  " + Tables.data());
        }
    }

    @Command(name = "dump", description = "Print a table as JSON: projects, dependencies, files, central, workspaces,"
            + " transcripts/sessions, transcripts/history, transcripts/projects, or a path to a .bin file")
    public static final class Dump implements Callable<Integer> {
        @Parameters(index = "0", description = "Table name or file") String name;

        @Override public Integer call() throws Exception {
            var file = name.endsWith(".bin") ? Path.of(name) : Tables.cache().resolve(name + ".bin");
            if (!Files.isRegularFile(file)) {
                System.err.println("proj: no table file " + file);
                return 1;
            }
            try (var in = Files.newInputStream(file)) {
                var out = littlejlib.proj.StdoutChannel.data();
                out.println(BinCodec.toJson(in));
                out.flush();
            }
            return 0;
        }
    }
}
